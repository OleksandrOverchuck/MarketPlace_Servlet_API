package pl.jollycart.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Obecne hasło jest wymagane")
        String currentPassword,

        @NotBlank(message = "Nowe hasło jest wymagane")
        @Size(min = 8, max = 100, message = "Hasło musi mieć od 8 do 100 znaków")
        String newPassword,

        @NotBlank(message = "Potwierdzenie hasła jest wymagane")
        String confirmPassword
) {
}
