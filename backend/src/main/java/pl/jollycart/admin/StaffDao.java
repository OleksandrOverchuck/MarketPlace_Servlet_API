package pl.jollycart.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import pl.jollycart.db.Db;
import pl.jollycart.db.RowMapper;
import pl.jollycart.offer.OfferStatus;
import pl.jollycart.offer.OfferType;
import pl.jollycart.user.AccountStatus;
import pl.jollycart.user.AuthProvider;
import pl.jollycart.user.Role;

/**
 * Zapytania panelu admina i moderacji: wyszukiwanie uzytkownikow i ogloszen (ze stronicowaniem)
 * oraz blokowanie kont. Odpowiednik Specification + findAll(Pageable) ze Spring Data.
 */
public final class StaffDao {

    private static final RowMapper<StaffUserResponse> USER_MAPPER = rs -> new StaffUserResponse(
            rs.getLong("id"),
            rs.getString("nickname"),
            rs.getString("email"),
            Role.valueOf(rs.getString("role")),
            AccountStatus.valueOf(rs.getString("status")),
            AuthProvider.valueOf(rs.getString("auth_provider")),
            rs.getString("avatar_url"),
            rs.getString("ban_reason"),
            rs.getObject("banned_at", LocalDateTime.class),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getLong("offers_count")
    );

    private static final RowMapper<StaffOfferResponse> OFFER_MAPPER = rs -> new StaffOfferResponse(
            rs.getLong("id"),
            rs.getString("title"),
            OfferStatus.valueOf(rs.getString("status")),
            OfferType.valueOf(rs.getString("type")),
            rs.getObject("price", BigDecimal.class),
            rs.getString("location"),
            rs.getLong("user_id"),
            rs.getString("nickname"),
            rs.getObject("created_at", LocalDateTime.class)
    );

    /** Liczba ogloszen liczona podzapytaniem - bez osobnego zapytania na kazdego uzytkownika. */
    private static final String USER_SELECT =
            "SELECT u.id, u.nickname, u.email, u.role, u.status, u.auth_provider, u.avatar_url, "
            + "u.ban_reason, u.banned_at, u.created_at, "
            + "(SELECT COUNT(*) FROM offers o WHERE o.user_id = u.id) AS offers_count "
            + "FROM users u ";

    private static final String OFFER_SELECT =
            "SELECT o.id, o.title, o.status, o.type, o.price, o.location, o.user_id, u.nickname, o.created_at "
            + "FROM offers o JOIN users u ON u.id = o.user_id ";

    /** Czesc WHERE razem z parametrami. */
    private record Where(String sql, List<Object> params) {
    }

    /* ---------------------------- uzytkownicy ---------------------------- */

    public Optional<StaffUserResponse> findUserById(long id) {
        return Db.one(USER_SELECT + "WHERE u.id = ?", USER_MAPPER, id);
    }

    public List<StaffUserResponse> searchUsers(String term, Role role, AccountStatus status,
                                               int page, int size) {
        Where where = userWhere(term, role, status);
        List<Object> params = new ArrayList<>(where.params());
        params.add(size);
        params.add((long) page * size);

        return Db.list(USER_SELECT + where.sql() + "ORDER BY u.id DESC LIMIT ? OFFSET ?",
                USER_MAPPER, params.toArray());
    }

    public long countUsers(String term, Role role, AccountStatus status) {
        Where where = userWhere(term, role, status);

        return Db.one("SELECT COUNT(*) FROM users u " + where.sql(),
                rs -> rs.getLong(1), where.params().toArray()).orElse(0L);
    }

    private Where userWhere(String term, Role role, AccountStatus status) {
        StringBuilder sql = new StringBuilder("WHERE 1 = 1 ");
        List<Object> params = new ArrayList<>();

        if (term != null) {
            String pattern = "%" + escapeLike(term.toLowerCase()) + "%";

            if (term.matches("\\d{1,18}")) {
                sql.append("AND (u.id = ? OR LOWER(u.nickname) LIKE ? ESCAPE '\\\\' "
                        + "OR LOWER(u.email) LIKE ? ESCAPE '\\\\') ");
                params.add(Long.parseLong(term));
            } else {
                sql.append("AND (LOWER(u.nickname) LIKE ? ESCAPE '\\\\' "
                        + "OR LOWER(u.email) LIKE ? ESCAPE '\\\\') ");
            }
            params.add(pattern);
            params.add(pattern);
        }
        if (role != null) {
            sql.append("AND u.role = ? ");
            params.add(role);
        }
        if (status != null) {
            sql.append("AND u.status = ? ");
            params.add(status);
        }

        return new Where(sql.toString(), params);
    }

    public void ban(long userId, String reason, long bannedById) {
        LocalDateTime now = LocalDateTime.now();

        Db.update(
                "UPDATE users SET status = ?, ban_reason = ?, banned_at = ?, banned_by_id = ?, "
                        + "updated_at = ? WHERE id = ?",
                AccountStatus.BANNED, reason, now, bannedById, now, userId
        );
    }

    public void unban(long userId) {
        Db.update(
                "UPDATE users SET status = ?, ban_reason = NULL, banned_at = NULL, banned_by_id = NULL, "
                        + "updated_at = ? WHERE id = ?",
                AccountStatus.ACTIVE, LocalDateTime.now(), userId
        );
    }

    /* ---------------------------- ogloszenia ---------------------------- */

    public List<StaffOfferResponse> searchOffers(String term, Long userId, OfferStatus status,
                                                 int page, int size) {
        Where where = offerWhere(term, userId, status);
        List<Object> params = new ArrayList<>(where.params());
        params.add(size);
        params.add((long) page * size);

        return Db.list(OFFER_SELECT + where.sql() + "ORDER BY o.id DESC LIMIT ? OFFSET ?",
                OFFER_MAPPER, params.toArray());
    }

    public long countOffers(String term, Long userId, OfferStatus status) {
        Where where = offerWhere(term, userId, status);

        return Db.one("SELECT COUNT(*) FROM offers o " + where.sql(),
                rs -> rs.getLong(1), where.params().toArray()).orElse(0L);
    }

    private Where offerWhere(String term, Long userId, OfferStatus status) {
        StringBuilder sql = new StringBuilder("WHERE 1 = 1 ");
        List<Object> params = new ArrayList<>();

        if (term != null) {
            String pattern = "%" + escapeLike(term.toLowerCase()) + "%";

            if (term.matches("\\d{1,18}")) {
                sql.append("AND (o.id = ? OR LOWER(o.title) LIKE ? ESCAPE '\\\\') ");
                params.add(Long.parseLong(term));
            } else {
                sql.append("AND LOWER(o.title) LIKE ? ESCAPE '\\\\' ");
            }
            params.add(pattern);
        }
        if (userId != null) {
            sql.append("AND o.user_id = ? ");
            params.add(userId);
        }
        if (status != null) {
            sql.append("AND o.status = ? ");
            params.add(status);
        }

        return new Where(sql.toString(), params);
    }

    /** Znaki specjalne LIKE (\ % _) traktujemy jak zwykle znaki. */
    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
