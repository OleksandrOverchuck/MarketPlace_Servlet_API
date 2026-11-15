package pl.jollycart.job;

import jakarta.validation.constraints.Size;

public record CreateJobApplicationRequest(
        @Size(max = 5000, message = "Wiadomość może mieć maksymalnie 5000 znaków")
        String message
) {
}
