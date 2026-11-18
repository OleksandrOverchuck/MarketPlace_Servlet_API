package pl.jollycart.payment;

/** Blad po stronie operatora platnosci (Stripe) albo brak konfiguracji platnosci. Konczy sie odpowiedzia 502. */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
