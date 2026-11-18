package pl.jollycart.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Jedyne miejsce, w ktorym liczymy kwoty zamowienia (port z wersji Spring Boot).
 *
 *   B     = cena towaru + dostawa
 *   F     = oplata serwisowa JollyCart = procent od B, nie mniej niz minimum
 *   Razem = (B + F + stala czesc oplaty Stripe) / (1 - stawka Stripe)
 *           zaokraglone W GORE do grosza
 *   Oplata operatora (Stripe) = Razem - B - F
 *
 * Dzieki liczeniu "wstecz" po potraceniu przez Stripe jego prowizji
 * zostaje dokladnie B + F. Stawka Stripe jest SZACUNKOWA - roznica
 * wzgledem prawdziwej prowizji obciaza platforme (albo zwieksza jej zysk).
 */
public final class PriceCalculator {

    public record PriceBreakdown(
            BigDecimal itemPrice,
            BigDecimal shippingCost,
            BigDecimal serviceFee,
            BigDecimal paymentFee,
            BigDecimal total
    ) {
        /** Kwota, ktora dostaje sprzedawca po zakonczeniu zamowienia. */
        public BigDecimal sellerPayout() {
            return itemPrice.add(shippingCost);
        }
    }

    private final BigDecimal serviceFeeRate;
    private final BigDecimal serviceFeeMin;
    private final BigDecimal stripeRate;
    private final BigDecimal stripeFixed;

    public PriceCalculator(PaymentConfig config) {
        this.serviceFeeRate = config.serviceFeePercent().movePointLeft(2);
        this.serviceFeeMin = config.serviceFeeMin();
        this.stripeRate = config.stripeFeePercent().movePointLeft(2);
        this.stripeFixed = config.stripeFeeFixed();

        if (stripeRate.signum() < 0 || stripeRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalStateException(
                    "PAYMENT_STRIPE_FEE_PERCENT musi byc z zakresu [0, 100)");
        }
    }

    public PriceBreakdown calculate(BigDecimal itemPrice, BigDecimal shippingCost) {

        BigDecimal price = itemPrice.setScale(2, RoundingMode.HALF_UP);
        BigDecimal shipping = (shippingCost == null ? BigDecimal.ZERO : shippingCost)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal base = price.add(shipping);

        BigDecimal serviceFee = base.multiply(serviceFeeRate)
                .setScale(2, RoundingMode.HALF_UP)
                .max(serviceFeeMin.setScale(2, RoundingMode.HALF_UP));

        BigDecimal net = base.add(serviceFee);

        BigDecimal total = net.add(stripeFixed)
                .divide(BigDecimal.ONE.subtract(stripeRate), 2, RoundingMode.CEILING);

        BigDecimal paymentFee = total.subtract(net);

        return new PriceBreakdown(price, shipping, serviceFee, paymentFee, total);
    }
}
