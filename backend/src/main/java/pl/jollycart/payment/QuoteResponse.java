package pl.jollycart.payment;

import java.math.BigDecimal;

/** Tabela kosztow pokazywana w oknie "Dane do wysylki" (tylko odczyt). */
public record QuoteResponse(
        Long offerId,
        String currency,
        BigDecimal itemPrice,
        BigDecimal shippingCost,
        BigDecimal serviceFee,
        BigDecimal paymentFee,
        BigDecimal total
) {
}
