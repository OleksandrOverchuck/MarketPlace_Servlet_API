package pl.jollycart.rating;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RateUserRequest(
        @Min(value = 1, message = "Ocena musi być od 1 do 5")
        @Max(value = 5, message = "Ocena musi być od 1 do 5")
        int stars
) {
}
