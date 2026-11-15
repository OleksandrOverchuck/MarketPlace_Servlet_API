package pl.jollycart.message;

import jakarta.validation.constraints.NotNull;

public record CreateConversationRequest(
        @NotNull(message = "ID użytkownika jest wymagane")
        Long otherUserId,

        @NotNull(message = "ID ogłoszenia jest wymagane")
        Long offerId
) {
}
