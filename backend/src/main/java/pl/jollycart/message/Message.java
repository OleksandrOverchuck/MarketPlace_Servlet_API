package pl.jollycart.message;

import java.time.LocalDateTime;

/** Wiersz tabeli messages. */
public record Message(
        Long id,
        Long conversationId,
        Long senderId,
        String content,
        Long attachmentId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
