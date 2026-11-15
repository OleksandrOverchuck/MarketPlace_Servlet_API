package pl.jollycart.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.jollycart.auth.EmailService;

/**
 * Wysyla maila o nowej wiadomosci. Wywolywany dopiero PO zatwierdzeniu transakcji
 * i w osobnym watku (zob. NotificationService), wiec SMTP nie spowalnia czatu.
 * Blad wysylki jest tylko logowany - nie psuje czatu.
 */
public final class MessageEmailListener {

    private static final Logger log = LoggerFactory.getLogger(MessageEmailListener.class);

    private final EmailService emailService;
    private final String frontendUrl;

    public MessageEmailListener(EmailService emailService, String frontendUrl) {
        this.emailService = emailService;
        this.frontendUrl = frontendUrl;
    }

    public void onMessageCreated(MessageEmailEvent event) {
        String chatUrl = frontendUrl
                + "/frontend/pages/chat.html?conversationId="
                + event.conversationId()
                + "&from=conversations";

        try {
            emailService.sendNewMessageNotification(
                    event.recipientEmail(),
                    event.recipientNickname(),
                    event.senderNickname(),
                    event.text(),
                    event.attachmentName(),
                    chatUrl
            );
        } catch (RuntimeException e) {
            log.warn("Nie udało się wysłać maila o nowej wiadomości (rozmowa {})",
                    event.conversationId(), e);
        }
    }
}
