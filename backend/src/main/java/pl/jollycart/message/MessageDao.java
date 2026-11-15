package pl.jollycart.message;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import pl.jollycart.db.Db;
import pl.jollycart.db.RowMapper;

/** Zapytania SQL do tabeli messages (odpowiednik MessageRepository). */
public final class MessageDao {

    private static final RowMapper<Message> MAPPER = rs -> new Message(
            rs.getLong("id"),
            rs.getLong("conversation_id"),
            rs.getLong("sender_id"),
            rs.getString("content"),
            rs.getObject("attachment_id", Long.class),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class)
    );

    /** Wiadomosc razem z nadawca i zalacznikiem - jednym zapytaniem (JOIN). */
    private static final RowMapper<MessageResponse> RESPONSE_MAPPER = rs -> new MessageResponse(
            rs.getLong("id"),
            rs.getLong("conversation_id"),
            rs.getLong("sender_id"),
            rs.getString("nickname"),
            rs.getString("avatar_url"),
            rs.getString("content"),
            rs.getObject("file_id", Long.class) == null
                    ? null
                    : new MessageResponse.AttachmentResponse(
                            rs.getLong("file_id"),
                            rs.getString("original_file_name"),
                            rs.getString("file_content_type"),
                            rs.getLong("file_size")),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class)
    );

    public Optional<Message> findById(long id) {
        return Db.one(
                "SELECT id, conversation_id, sender_id, content, attachment_id, created_at, updated_at "
                        + "FROM messages WHERE id = ?",
                MAPPER, id
        );
    }

    public List<MessageResponse> findResponsesByConversationId(long conversationId) {
        return Db.list(
                "SELECT m.id, m.conversation_id, m.sender_id, u.nickname, u.avatar_url, m.content, "
                        + "m.created_at, m.updated_at, f.id AS file_id, f.original_file_name, "
                        + "f.content_type AS file_content_type, f.file_size "
                        + "FROM messages m "
                        + "JOIN users u ON u.id = m.sender_id "
                        + "LEFT JOIN stored_files f ON f.id = m.attachment_id "
                        + "WHERE m.conversation_id = ? ORDER BY m.created_at ASC, m.id ASC",
                RESPONSE_MAPPER, conversationId
        );
    }

    public Message insert(long conversationId, long senderId, String content, Long attachmentId) {
        LocalDateTime now = LocalDateTime.now();

        long id = Db.insert(
                "INSERT INTO messages (conversation_id, sender_id, content, attachment_id, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                conversationId, senderId, content, attachmentId, now, now
        );

        return new Message(id, conversationId, senderId, content, attachmentId, now, now);
    }
}
