package pl.jollycart.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMessageRequest(
        @NotBlank(message = "Treść wiadomości jest wymagana")
        @Size(max = 5000, message = "Wiadomość może mieć maksymalnie 5000 znaków")
        String content
) {
}
