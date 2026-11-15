package pl.jollycart.notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import pl.jollycart.db.Db;
import pl.jollycart.db.RowMapper;

/** Zapytania SQL do tabeli notifications (odpowiednik NotificationRepository). */
public final class NotificationDao {

    private static final RowMapper<Notification> MAPPER = rs -> new Notification(
            rs.getLong("id"),
            rs.getLong("recipient_id"),
            rs.getLong("sender_id"),
            rs.getLong("conversation_id"),
            rs.getLong("message_id"),
            NotificationType.valueOf(rs.getString("type")),
            rs.getBoolean("is_read"),
            rs.getObject("created_at", LocalDateTime.class)
    );

    /** Powiadomienie razem z nadawca, trescia wiadomosci i nazwa zalacznika (JOIN). */
    private static final RowMapper<NotificationResponse> RESPONSE_MAPPER = rs -> {
        String content = rs.getString("content");
        String attachmentName = rs.getString("original_file_name");

        // wiadomosc bez tekstu, ale z zalacznikiem - pokazujemy nazwe pliku
        if ((content == null || content.isBlank()) && attachmentName != null) {
            content = "\uD83D\uDCCE " + attachmentName;
        }

        return new NotificationResponse(
                rs.getLong("id"),
                NotificationType.valueOf(rs.getString("type")),
                rs.getBoolean("is_read"),
                rs.getLong("conversation_id"),
                rs.getLong("message_id"),
                rs.getLong("sender_id"),
                rs.getString("nickname"),
                rs.getString("avatar_url"),
                content,
                rs.getObject("created_at", LocalDateTime.class)
        );
    };

    public Optional<Notification> findById(long id) {
        return Db.one(
                "SELECT id, recipient_id, sender_id, conversation_id, message_id, type, is_read, created_at "
                        + "FROM notifications WHERE id = ?",
                MAPPER, id
        );
    }

    public List<NotificationResponse> findResponsesByRecipientId(long recipientId) {
        return Db.list(
                "SELECT n.id, n.type, n.is_read, n.conversation_id, n.message_id, n.sender_id, "
                        + "u.nickname, u.avatar_url, m.content, f.original_file_name, n.created_at "
                        + "FROM notifications n "
                        + "JOIN users u ON u.id = n.sender_id "
                        + "JOIN messages m ON m.id = n.message_id "
                        + "LEFT JOIN stored_files f ON f.id = m.attachment_id "
                        + "WHERE n.recipient_id = ? ORDER BY n.created_at DESC, n.id DESC",
                RESPONSE_MAPPER, recipientId
        );
    }

    public long countUnread(long recipientId) {
        return Db.one(
                "SELECT COUNT(*) FROM notifications WHERE recipient_id = ? AND is_read = 0",
                rs -> rs.getLong(1), recipientId
        ).orElse(0L);
    }

    public boolean existsByRecipientAndConversationCreatedAfter(long recipientId, long conversationId,
                                                                LocalDateTime after) {
        return Db.exists(
                "SELECT 1 FROM notifications WHERE recipient_id = ? AND conversation_id = ? "
                        + "AND created_at > ? LIMIT 1",
                recipientId, conversationId, after
        );
    }

    public void insertMessageNotification(long recipientId, long senderId, long conversationId, long messageId) {
        Db.insert(
                "INSERT INTO notifications (recipient_id, sender_id, conversation_id, message_id, "
                        + "type, is_read, created_at) VALUES (?, ?, ?, ?, ?, 0, ?)",
                recipientId, senderId, conversationId, messageId,
                NotificationType.MESSAGE, LocalDateTime.now()
        );
    }

    public void markRead(long id) {
        Db.update("UPDATE notifications SET is_read = 1 WHERE id = ?", id);
    }

    public void markAllRead(long recipientId) {
        Db.update("UPDATE notifications SET is_read = 1 WHERE recipient_id = ?", recipientId);
    }
}
