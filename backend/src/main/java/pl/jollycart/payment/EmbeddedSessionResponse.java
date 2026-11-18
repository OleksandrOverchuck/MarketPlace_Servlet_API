package pl.jollycart.payment;

public record EmbeddedSessionResponse(
        String clientSecret,     // jednorazowy sekret sesji dla komponentu Stripe
        String publishableKey    // klucz publiczny (pk_test_...), bezpieczny dla przegladarki
) {
}
