package pl.jollycart.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BanUserRequest(
        @NotBlank(message = "Powód blokady jest wymagany")
        @Size(min = 3, max = 500, message = "Powód blokady musi mieć od 3 do 500 znaków")
        String reason
) {
}
