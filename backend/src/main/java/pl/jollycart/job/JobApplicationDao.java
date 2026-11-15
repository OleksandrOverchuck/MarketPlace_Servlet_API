package pl.jollycart.job;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import pl.jollycart.db.Db;
import pl.jollycart.db.RowMapper;

/** Zapytania SQL do tabeli job_applications (odpowiednik JobApplicationRepository). */
public final class JobApplicationDao {

    private static final RowMapper<JobApplicationResponse> RESPONSE_MAPPER = rs -> new JobApplicationResponse(
            rs.getLong("id"),
            rs.getLong("offer_id"),
            rs.getString("title"),
            rs.getLong("user_id"),
            rs.getString("nickname"),
            JobApplicationStatus.valueOf(rs.getString("status")),
            rs.getString("message"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class)
    );

    /** Aplikacja razem z tytulem oferty i nickiem kandydata - jednym zapytaniem (JOIN). */
    private static final String RESPONSE_SELECT =
            "SELECT a.id, a.offer_id, o.title, a.user_id, u.nickname, a.status, a.message, "
            + "a.created_at, a.updated_at "
            + "FROM job_applications a "
            + "JOIN offers o ON o.id = a.offer_id "
            + "JOIN users u ON u.id = a.user_id ";

    public Optional<JobApplication> findById(long id) {
        return Db.one(
                "SELECT id, offer_id, user_id, cv_file_id FROM job_applications WHERE id = ?",
                rs -> new JobApplication(
                        rs.getLong("id"), rs.getLong("offer_id"), rs.getLong("user_id"),
                        rs.getObject("cv_file_id", Long.class)),
                id
        );
    }

    public boolean existsByOfferAndUser(long offerId, long userId) {
        return Db.exists(
                "SELECT 1 FROM job_applications WHERE offer_id = ? AND user_id = ? LIMIT 1",
                offerId, userId
        );
    }

    public long insert(long offerId, long userId, String message, LocalDateTime now) {
        return Db.insert(
                "INSERT INTO job_applications (offer_id, user_id, cv_file_id, status, message, "
                        + "created_at, updated_at) VALUES (?, ?, NULL, ?, ?, ?, ?)",
                offerId, userId, JobApplicationStatus.PENDING, message, now, now
        );
    }

    public List<JobApplicationResponse> findResponsesByUserId(long userId) {
        return Db.list(
                RESPONSE_SELECT + "WHERE a.user_id = ? ORDER BY a.created_at DESC, a.id DESC",
                RESPONSE_MAPPER, userId
        );
    }

    public List<JobApplicationResponse> findResponsesByOfferId(long offerId) {
        return Db.list(
                RESPONSE_SELECT + "WHERE a.offer_id = ? ORDER BY a.created_at DESC, a.id DESC",
                RESPONSE_MAPPER, offerId
        );
    }

    public void attachCv(long applicationId, long cvFileId) {
        Db.update("UPDATE job_applications SET cv_file_id = ?, updated_at = ? WHERE id = ?",
                cvFileId, LocalDateTime.now(), applicationId);
    }
}
