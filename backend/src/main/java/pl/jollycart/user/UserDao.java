package pl.jollycart.user;

import java.time.LocalDateTime;
import java.util.Optional;

import pl.jollycart.db.Db;
import pl.jollycart.db.RowMapper;

/** Zapytania SQL do tabeli users (odpowiednik UserRepository). */
public final class UserDao {

    private static final String COLUMNS =
            "id, nickname, email, password_hash, auth_provider, provider_id, avatar_url, "
            + "phone, location, role, status, ban_reason, banned_at, banned_by_id, "
            + "password_change_required, created_at, updated_at";

    private static final RowMapper<User> MAPPER = rs -> new User(
            rs.getLong("id"),
            rs.getString("nickname"),
            rs.getString("email"),
            rs.getString("password_hash"),
            AuthProvider.valueOf(rs.getString("auth_provider")),
            rs.getString("provider_id"),
            rs.getString("avatar_url"),
            rs.getString("phone"),
            rs.getString("location"),
            Role.valueOf(rs.getString("role")),
            AccountStatus.valueOf(rs.getString("status")),
            rs.getString("ban_reason"),
            rs.getObject("banned_at", LocalDateTime.class),
            rs.getObject("banned_by_id", Long.class),
            rs.getBoolean("password_change_required"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class)
    );

    public Optional<User> findById(long id) {
        return Db.one("SELECT " + COLUMNS + " FROM users WHERE id = ?", MAPPER, id);
    }

    public Optional<User> findByEmail(String email) {
        return Db.one("SELECT " + COLUMNS + " FROM users WHERE email = ?", MAPPER, email);
    }

    public Optional<User> findByAuthProviderAndProviderId(AuthProvider provider, String providerId) {
        return Db.one(
                "SELECT " + COLUMNS + " FROM users WHERE auth_provider = ? AND provider_id = ?",
                MAPPER, provider, providerId
        );
    }

    public boolean existsByEmail(String email) {
        return Db.exists("SELECT 1 FROM users WHERE email = ? LIMIT 1", email);
    }

    public boolean existsByNickname(String nickname) {
        return Db.exists("SELECT 1 FROM users WHERE nickname = ? LIMIT 1", nickname);
    }

    /** Tworzy konto lokalne (rejestracja haslem). Zwraca id nowego uzytkownika. */
    public long createLocal(String nickname, String email, String passwordHash) {
        LocalDateTime now = LocalDateTime.now();

        return Db.insert(
                "INSERT INTO users (nickname, email, password_hash, auth_provider, role, status, "
                        + "password_change_required, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'LOCAL', 'USER', 'ACTIVE', 0, ?, ?)",
                nickname, email, passwordHash, now, now
        );
    }

    /** Tworzy konto zalozone przez Google (bez hasla). Zwraca id nowego uzytkownika. */
    public long createGoogle(String nickname, String email, String providerId, String avatarUrl) {
        LocalDateTime now = LocalDateTime.now();

        return Db.insert(
                "INSERT INTO users (nickname, email, password_hash, auth_provider, provider_id, "
                        + "avatar_url, role, status, password_change_required, created_at, updated_at) "
                        + "VALUES (?, ?, NULL, 'GOOGLE', ?, ?, 'USER', 'ACTIVE', 0, ?, ?)",
                nickname, email, providerId, avatarUrl, now, now
        );
    }

    /**
     * Podpina konto Google do istniejacego konta (np. lokalnego). Provider zostaje bez zmian,
     * awatar z Google ustawiamy tylko wtedy, gdy uzytkownik nie ma wlasnego.
     */
    public void linkGoogle(long userId, String providerId, String avatarUrl) {
        Db.update(
                "UPDATE users SET provider_id = ?, avatar_url = COALESCE(avatar_url, ?), "
                        + "updated_at = ? WHERE id = ?",
                providerId, avatarUrl, LocalDateTime.now(), userId
        );
    }

    public boolean existsByRole(Role role) {
        return Db.exists("SELECT 1 FROM users WHERE role = ? LIMIT 1", role);
    }

    public void changeRole(long userId, Role role) {
        Db.update("UPDATE users SET role = ?, updated_at = ? WHERE id = ?",
                role, LocalDateTime.now(), userId);
    }

    /** Tworzy konto administratora z haslem lokalnym. Zwraca id nowego uzytkownika. */
    public long createAdmin(String nickname, String email, String passwordHash,
                            boolean passwordChangeRequired) {
        LocalDateTime now = LocalDateTime.now();

        return Db.insert(
                "INSERT INTO users (nickname, email, password_hash, auth_provider, role, status, "
                        + "password_change_required, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'LOCAL', 'ADMIN', 'ACTIVE', ?, ?, ?)",
                nickname, email, passwordHash, passwordChangeRequired, now, now
        );
    }
}
