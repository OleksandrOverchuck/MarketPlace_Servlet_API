package pl.jollycart.notification;

/** Dane potrzebne do wyslania maila o nowej wiadomosci (same typy proste). */
public record MessageEmailEvent(
        String recipientEmail,
        String recipientNickname,
        String senderNickname,
        Long conversationId,
        String text,
        String attachmentName
) {
}
