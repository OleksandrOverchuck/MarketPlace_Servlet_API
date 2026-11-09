package pl.jollycart.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyEmailRequest(
        @NotBlank(message = "Email jest wymagany")
        @Email(message = "Podaj poprawny adres email")
        String email,

        @NotBlank(message = "Kod jest wymagany")
        @Pattern(regexp = "\\d{4,8}", message = "Kod składa się wyłącznie z cyfr")
        String code
) {
}
