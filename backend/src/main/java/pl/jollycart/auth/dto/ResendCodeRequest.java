package pl.jollycart.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendCodeRequest(
        @NotBlank(message = "Email jest wymagany")
        @Email(message = "Podaj poprawny adres email")
        String email
) {
}
