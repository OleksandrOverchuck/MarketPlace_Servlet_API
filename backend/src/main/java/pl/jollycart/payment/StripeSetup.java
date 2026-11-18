package pl.jollycart.payment;

import com.stripe.Stripe;

/** Ustawia klucz Stripe przy starcie aplikacji (odpowiednik StripeConfig ze Spring Boot). */
public final class StripeSetup {

    private StripeSetup() {
    }

    /** Wywolaj raz w Main. Bez klucza aplikacja dziala, ale bez platnosci. */
    public static void init(PaymentConfig config) {
        if (!config.stripeConfigured()) {
            return;
        }

        String key = config.stripeSecretKey();

        // Zabezpieczenie: tylko klucz testowy
        if (!key.startsWith("sk_test_") && !key.startsWith("rk_test_")) {
            throw new IllegalStateException("STRIPE_SECRET_KEY musi być kluczem testowym (sk_test_...).");
        }

        Stripe.apiKey = key;
    }

    /** Rzuca PaymentGatewayException (502), gdy klucz nie zostal ustawiony. */
    public static void assertConfigured() {
        if (Stripe.apiKey == null || Stripe.apiKey.isBlank()) {
            throw new PaymentGatewayException("Płatności nie są skonfigurowane (brak STRIPE_SECRET_KEY).");
        }
    }
}
