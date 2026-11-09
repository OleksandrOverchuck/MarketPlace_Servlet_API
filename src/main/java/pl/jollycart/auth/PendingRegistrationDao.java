package pl.jollycart.auth;

import java.time.LocalDateTime;
import java.util.Optional;

import pl.jollycart.db.Db;
import pl.jollycart.db.RowMapper;

/** Zapytania SQL do tabeli pending_registrations. */
public final class PendingRegistrationDao {

    private static final RowMapper<PendingRegistration> MAPPER = rs -> new PendingRegistration(
            rs.getLong("id"),
            rs.getString("email"),
            rs.getString("nickname"),
            rs.getString("password_hash"),
            rs.getString("code_hash"),
            rs.getObject("expires_at", LocalDateTime.class),
            rs.getObject("last_sent_at", LocalDateTime.class),
            rs.getInt("attempts"),
            rs.getObject("created_at", LocalDateTime.class)
    );

    public Optional<PendingRegistration> findByEmail(String email) {
        return Db.one(
                "SELECT id, email, nickname, password_hash, code_hash, expires_at, "
                        + "last_sent_at, attempts, created_at "
                        + "FROM pending_registrations WHERE email = ?",
                MAPPER, email
        );
    }

    public boolean existsByNicknameAndEmailNot(String nickname, String email) {
        return Db.exists(
                "SELECT 1 FROM pending_registrations WHERE nickname = ? AND email <> ? LIMIT 1",
                nickname, email
        );
    }

    public void deleteByCreatedAtBefore(LocalDateTime threshold) {
        Db.update("DELETE FROM pending_registrations WHERE created_at < ?", threshold);
    }

    public void insert(String email, String nickname, String passwordHash, String codeHash,
                       LocalDateTime expiresAt, LocalDateTime lastSentAt) {
        Db.insert(
                "INSERT INTO pending_registrations (email, nickname, password_hash, code_hash, "
                        + "expires_at, last_sent_at, attempts, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 0, ?)",
                email, nickname, passwordHash, codeHash, expiresAt, lastSentAt, LocalDateTime.now()
        );
    }

    /** Ponowne rozpoczecie rejestracji tym samym e-mailem - nadpisuje poprzednia probe. */
    public void restart(long id, String email, String nickname, String passwordHash,
                        String codeHash, LocalDateTime expiresAt, LocalDateTime lastSentAt) {
        Db.update(
                "UPDATE pending_registrations SET email = ?, nickname = ?, password_hash = ?, "
                        + "code_hash = ?, expires_at = ?, last_sent_at = ?, attempts = 0 WHERE id = ?",
                email, nickname, passwordHash, codeHash, expiresAt, lastSentAt, id
        );
    }

    /** Nowy kod dla istniejacej rejestracji (przycisk "wyslij ponownie"). */
    public void updateCode(long id, String codeHash, LocalDateTime expiresAt, LocalDateTime lastSentAt) {
        Db.update(
                "UPDATE pending_registrations SET code_hash = ?, expires_at = ?, "
                        + "last_sent_at = ?, attempts = 0 WHERE id = ?",
                codeHash, expiresAt, lastSentAt, id
        );
    }

    public void updateAttempts(long id, int attempts) {
        Db.update("UPDATE pending_registrations SET attempts = ? WHERE id = ?", attempts, id);
    }

    public void delete(long id) {
        Db.update("DELETE FROM pending_registrations WHERE id = ?", id);
    }
}
