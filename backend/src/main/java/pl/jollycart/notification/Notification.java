package pl.jollycart.notification;

import java.time.LocalDateTime;

/** Wiersz tabeli notifications. */
public record Notification(
        Long id,
        Long recipientId,
        Long senderId,
        Long conversationId,
        Long messageId,
        NotificationType type,
        boolean read,
        LocalDateTime createdAt
) {
}
