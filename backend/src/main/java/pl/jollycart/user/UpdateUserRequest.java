package pl.jollycart.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank(message = "Nickname jest wymagany")
        @Size(min = 3, max = 30, message = "Nickname musi mieć od 3 do 30 znaków")
        String nickname,

        @Size(max = 1000, message = "URL avatara może mieć maksymalnie 1000 znaków")
        String avatarUrl,

        @Size(max = 30, message = "Numer telefonu może mieć maksymalnie 30 znaków")
        String phone,

        @Size(max = 100, message = "Lokalizacja może mieć maksymalnie 100 znaków")
        String location
) {
}
