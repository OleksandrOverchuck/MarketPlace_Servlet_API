package pl.jollycart.message;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pl.jollycart.db.Db;
import pl.jollycart.db.RowMapper;
import pl.jollycart.message.ConversationResponse.ParticipantResponse;

/** Zapytania SQL do tabel conversations i conversation_participants. */
public final class ConversationDao {

    private static final RowMapper<Conversation> MAPPER = rs -> new Conversation(
            rs.getLong("id"),
            rs.getObject("offer_id", Long.class),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class)
    );

    /** Uczestnik rozmowy z adresem e-mail (do powiadomien). */
    public record ParticipantUser(Long id, String email, String nickname) {
    }

    private record ParticipantRow(Long conversationId, ParticipantResponse participant) {
    }

    public Optional<Conversation> findById(long id) {
        return Db.one(
                "SELECT id, offer_id, created_at, updated_at FROM conversations WHERE id = ?",
                MAPPER, id
        );
    }

    public boolean existsById(long id) {
        return Db.exists("SELECT 1 FROM conversations WHERE id = ? LIMIT 1", id);
    }

    /** Rozmowa dwoch uzytkownikow o danym ogloszeniu (jesli juz istnieje). */
    public Optional<Conversation> findBetweenUsersAndOffer(long userId1, long userId2, long offerId) {
        return Db.one(
                "SELECT c.id, c.offer_id, c.created_at, c.updated_at "
                        + "FROM conversation_participants cp1 "
                        + "JOIN conversation_participants cp2 ON cp2.conversation_id = cp1.conversation_id "
                        + "JOIN conversations c ON c.id = cp1.conversation_id "
                        + "WHERE cp1.user_id = ? AND cp2.user_id = ? AND c.offer_id = ? "
                        + "LIMIT 1",
                MAPPER, userId1, userId2, offerId
        );
    }

    public List<Conversation> findByParticipantUserId(long userId) {
        return Db.list(
                "SELECT c.id, c.offer_id, c.created_at, c.updated_at "
                        + "FROM conversation_participants cp "
                        + "JOIN conversations c ON c.id = cp.conversation_id "
                        + "WHERE cp.user_id = ? ORDER BY cp.id",
                MAPPER, userId
        );
    }

    public boolean isParticipant(long conversationId, long userId) {
        return Db.exists(
                "SELECT 1 FROM conversation_participants WHERE conversation_id = ? AND user_id = ? LIMIT 1",
                conversationId, userId
        );
    }

    public long insert(long offerId, LocalDateTime now) {
        return Db.insert(
                "INSERT INTO conversations (offer_id, created_at, updated_at) VALUES (?, ?, ?)",
                offerId, now, now
        );
    }

    public void insertParticipant(long conversationId, long userId, LocalDateTime now) {
        Db.insert(
                "INSERT INTO conversation_participants (conversation_id, user_id, joined_at) VALUES (?, ?, ?)",
                conversationId, userId, now
        );
    }

    /** Uczestnicy wielu rozmow naraz (jedno zapytanie zamiast jednego na rozmowe). */
    public Map<Long, List<ParticipantResponse>> findParticipants(Collection<Long> conversationIds) {
        Map<Long, List<ParticipantResponse>> result = new LinkedHashMap<>();

        if (conversationIds.isEmpty()) {
            return result;
        }

        String placeholders = String.join(",", Collections.nCopies(conversationIds.size(), "?"));

        List<ParticipantRow> rows = Db.list(
                "SELECT cp.conversation_id, u.id, u.nickname, u.avatar_url "
                        + "FROM conversation_participants cp "
                        + "JOIN users u ON u.id = cp.user_id "
                        + "WHERE cp.conversation_id IN (" + placeholders + ") ORDER BY cp.id",
                rs -> new ParticipantRow(
                        rs.getLong("conversation_id"),
                        new ParticipantResponse(rs.getLong("id"), rs.getString("nickname"),
                                rs.getString("avatar_url"))),
                conversationIds.toArray()
        );

        for (ParticipantRow row : rows) {
            result.computeIfAbsent(row.conversationId(), key -> new java.util.ArrayList<>())
                    .add(row.participant());
        }

        return result;
    }

    public List<ParticipantUser> findParticipantUsers(long conversationId) {
        return Db.list(
                "SELECT u.id, u.email, u.nickname FROM conversation_participants cp "
                        + "JOIN users u ON u.id = cp.user_id WHERE cp.conversation_id = ? ORDER BY cp.id",
                rs -> new ParticipantUser(rs.getLong("id"), rs.getString("email"), rs.getString("nickname")),
                conversationId
        );
    }
}
