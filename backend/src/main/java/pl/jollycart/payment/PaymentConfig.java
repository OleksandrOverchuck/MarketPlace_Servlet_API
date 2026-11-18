package pl.jollycart.payment;

import java.math.BigDecimal;

/**
 * Konfiguracja platnosci Stripe. Odpowiednik sekcji "Platnosci Stripe" z application.properties
 * w wersji Spring Boot. Wartosci pochodza ze zmiennych srodowiskowych (te same nazwy:
 * STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET, STRIPE_PUBLISHABLE_KEY), z wartosciami domyslnymi.
 *
 * Tylko sandbox Stripe - klucze testowe (sk_test_..., pk_test_..., whsec_...).
 */
public record PaymentConfig(
        String stripeSecretKey,
        String stripeWebhookSecret,
        String stripePublishableKey,
        String currency,
        int checkoutMinutes,
        int shipHours,
        int confirmHours,
        int autoDeliverDays,
        BigDecimal serviceFeePercent,
        BigDecimal serviceFeeMin,
        BigDecimal stripeFeePercent,
        BigDecimal stripeFeeFixed
) {

    public static PaymentConfig load() {
        return new PaymentConfig(
                env("STRIPE_SECRET_KEY", ""),
                env("STRIPE_WEBHOOK_SECRET", ""),
                env("STRIPE_PUBLISHABLE_KEY", ""),
                env("PAYMENT_CURRENCY", "pln"),                              // app.payment.currency
                Integer.parseInt(env("PAYMENT_CHECKOUT_MINUTES", "30")),     // app.payment.checkout-minutes
                Integer.parseInt(env("PAYMENT_SHIP_HOURS", "48")),           // app.payment.ship-hours
                Integer.parseInt(env("PAYMENT_CONFIRM_HOURS", "24")),        // app.payment.confirm-hours
                Integer.parseInt(env("PAYMENT_AUTO_DELIVER_DAYS", "7")),     // app.payment.auto-deliver-days
                new BigDecimal(env("PAYMENT_SERVICE_FEE_PERCENT", "3")),     // app.payment.service-fee-percent
                new BigDecimal(env("PAYMENT_SERVICE_FEE_MIN", "2.00")),      // app.payment.service-fee-min
                new BigDecimal(env("PAYMENT_STRIPE_FEE_PERCENT", "3.3")),    // app.payment.stripe-fee-percent
                new BigDecimal(env("PAYMENT_STRIPE_FEE_FIXED", "0.00"))      // app.payment.stripe-fee-fixed
        );
    }

    /** Czy podano klucz Stripe. Bez niego platnosci sa wylaczone (aplikacja i tak startuje). */
    public boolean stripeConfigured() {
        return stripeSecretKey != null && !stripeSecretKey.isBlank();
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }
}
