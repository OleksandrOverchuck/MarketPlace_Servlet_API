package pl.jollycart.notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

import pl.jollycart.db.Db;
import pl.jollycart.file.StoredFile;
import pl.jollycart.message.ConversationDao;
import pl.jollycart.message.ConversationDao.ParticipantUser;
import pl.jollycart.message.Message;
import pl.jollycart.user.User;

/** Powiadomienia o nowych wiadomosciach (odpowiednik NotificationService). */
public final class NotificationService {

    private static final long EMAIL_COOLDOWN_MINUTES = 10;

    private final NotificationDao notificationDao;
    private final ConversationDao conversationDao;
    private final MessageEmailListener emailListener;
    private final Executor asyncExecutor;

    public NotificationService(
            NotificationDao notificationDao,
            ConversationDao conversationDao,
            MessageEmailListener emailListener,
            Executor asyncExecutor
    ) {
        this.notificationDao = notificationDao;
        this.conversationDao = conversationDao;
        this.emailListener = emailListener;
        this.asyncExecutor = asyncExecutor;
    }

    /*
     * Tworzymy powiadomienie dla kazdego uczestnika rozmowy, ktory NIE jest autorem wiadomosci.
     * Wywolywane wewnatrz transakcji zapisu wiadomosci.
     */
    public void createMessageNotifications(Message message, User sender, StoredFile attachment) {
        for (ParticipantUser recipient : conversationDao.findParticipantUsers(message.conversationId())) {

            // Nie wysylamy powiadomienia samemu sobie
            if (recipient.id().equals(sender.id())) {
                continue;
            }

            /*
             * Najwyzej jeden mail na rozmowe w oknie EMAIL_COOLDOWN_MINUTES -
             * zeby zywa wymiana wiadomosci nie zasypala skrzynki.
             */
            boolean shouldEmail = !notificationDao.existsByRecipientAndConversationCreatedAfter(
                    recipient.id(), message.conversationId(),
                    LocalDateTime.now().minusMinutes(EMAIL_COOLDOWN_MINUTES));

            notificationDao.insertMessageNotification(
                    recipient.id(), sender.id(), message.conversationId(), message.id());

            if (shouldEmail) {
                MessageEmailEvent event = new MessageEmailEvent(
                        recipient.email(),
                        recipient.nickname(),
                        sender.nickname(),
                        message.conversationId(),
                        message.content(),
                        attachment != null ? attachment.originalFileName() : null
                );

                // Mail wychodzi dopiero PO zatwierdzeniu transakcji (jesli zapis wiadomosci
                // sie nie uda, mail nie wyjdzie) i w osobnym watku (SMTP nie spowalnia czatu).
                Db.afterCommit(() -> asyncExecutor.execute(() -> emailListener.onMessageCreated(event)));
            }
        }
    }

    public List<NotificationResponse> getUserNotifications(User user) {
        return notificationDao.findResponsesByRecipientId(user.id());
    }

    public long getUnreadCount(User user) {
        return notificationDao.countUnread(user.id());
    }

    public void markAsRead(long notificationId, User user) {
        Notification notification = notificationDao.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Powiadomienie nie zostało znalezione"));

        if (!notification.recipientId().equals(user.id())) {
            throw new IllegalArgumentException("Nie masz dostępu do tego powiadomienia");
        }

        notificationDao.markRead(notificationId);
    }

    public void markAllAsRead(User user) {
        notificationDao.markAllRead(user.id());
    }
}
