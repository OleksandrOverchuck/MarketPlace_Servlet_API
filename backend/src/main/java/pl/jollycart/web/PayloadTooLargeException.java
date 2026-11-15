package pl.jollycart.web;

/** Przeslany plik albo cale zadanie przekracza dozwolony rozmiar - HTTP 413. */
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(String message) {
        super(message);
    }
}
