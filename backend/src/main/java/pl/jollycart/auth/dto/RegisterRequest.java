package pl.jollycart.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Nickname jest wymagany")
        @Size(min = 3, max = 30, message = "Nickname musi mieć od 3 do 30 znaków")
        String nickname,

        @NotBlank(message = "Email jest wymagany")
        @Email(message = "Podaj poprawny adres email")
        @Size(max = 255, message = "Email może mieć maksymalnie 255 znaków")
        String email,

        @NotBlank(message = "Hasło jest wymagane")
        @Size(min = 8, max = 100, message = "Hasło musi mieć od 8 do 100 znaków")
        String password,

        @NotBlank(message = "Potwierdzenie hasła jest wymagane")
        String confirmPassword
) {
}
