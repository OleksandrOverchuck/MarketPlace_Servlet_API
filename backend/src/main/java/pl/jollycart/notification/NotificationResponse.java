package pl.jollycart.notification;

import java.time.LocalDateTime;

/** JSON powiadomienia - identyczny z NotificationResponse w wersji Spring Boot. */
public record NotificationResponse(
        Long id,
        NotificationType type,
        boolean read,
        Long conversationId,
        Long messageId,
        Long senderId,
        String senderNickname,
        String senderAvatarUrl,
        String content,
        LocalDateTime createdAt
) {
}
