package pl.jollycart.web;

import java.util.Map;

/** Bledy walidacji DTO: pole -> komunikat. Zwracane jako HTTP 400 z mapa pol. */
public class RequestValidationException extends RuntimeException {

    private final transient Map<String, String> errors;

    public RequestValidationException(Map<String, String> errors) {
        super("Niepoprawne dane żądania");
        this.errors = errors;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
