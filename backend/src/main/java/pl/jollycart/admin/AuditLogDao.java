package pl.jollycart.admin;

import java.time.LocalDateTime;
import java.util.List;

import pl.jollycart.db.Db;
import pl.jollycart.db.RowMapper;

/** Zapytania SQL do tabeli audit_logs (odpowiednik AuditLogRepository). */
public final class AuditLogDao {

    private static final RowMapper<AuditLogResponse> MAPPER = rs -> new AuditLogResponse(
            rs.getLong("id"),
            rs.getObject("actor_id", Long.class),
            rs.getString("actor_nickname"),
            AuditAction.valueOf(rs.getString("action")),
            rs.getString("target_type"),
            rs.getObject("target_id", Long.class),
            rs.getString("details"),
            rs.getObject("created_at", LocalDateTime.class)
    );

    public void insert(Long actorId, String actorNickname, AuditAction action,
                       String targetType, Long targetId, String details) {
        Db.insert(
                "INSERT INTO audit_logs (actor_id, actor_nickname, action, target_type, target_id, "
                        + "details, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                actorId, actorNickname, action, targetType, targetId, details, LocalDateTime.now()
        );
    }

    /** Najnowsze wpisy na poczatku. */
    public List<AuditLogResponse> findPage(int page, int size) {
        return Db.list(
                "SELECT id, actor_id, actor_nickname, action, target_type, target_id, details, created_at "
                        + "FROM audit_logs ORDER BY id DESC LIMIT ? OFFSET ?",
                MAPPER, size, (long) page * size
        );
    }

    public long count() {
        return Db.one("SELECT COUNT(*) FROM audit_logs", rs -> rs.getLong(1)).orElse(0L);
    }
}
