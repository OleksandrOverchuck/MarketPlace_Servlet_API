package pl.jollycart.web;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** Walidacja DTO adnotacjami Jakarta Validation (Hibernate Validator) - jak @Valid w Springu. */
public final class RequestValidator {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private RequestValidator() {
    }

    public static <T> T validated(T object) {
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(object);

        if (!violations.isEmpty()) {
            Map<String, String> errors = new LinkedHashMap<>();

            violations.stream()
                    .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));

            throw new RequestValidationException(errors);
        }

        return object;
    }
}
