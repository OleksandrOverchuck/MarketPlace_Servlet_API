package pl.jollycart.dispute;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import pl.jollycart.db.Db;
import pl.jollycart.db.RowMapper;

/** Zapytania SQL do tabeli dispute_images (odpowiednik DisputeImageRepository). */
public final class DisputeImageDao {

    private static final String SELECT =
            "SELECT id, dispute_id, file_name, stored_file_name, content_type, file_size, created_at "
                    + "FROM dispute_images ";

    private static final RowMapper<DisputeImage> MAPPER = rs -> new DisputeImage(
            rs.getLong("id"),
            rs.getLong("dispute_id"),
            rs.getString("file_name"),
            rs.getString("stored_file_name"),
            rs.getString("content_type"),
            rs.getLong("file_size"),
            rs.getObject("created_at", LocalDateTime.class)
    );

    public List<DisputeImage> findByDisputeId(long disputeId) {
        return Db.list(SELECT + "WHERE dispute_id = ? ORDER BY id ASC", MAPPER, disputeId);
    }

    public Optional<DisputeImage> findByIdAndDisputeId(long imageId, long disputeId) {
        return Db.one(SELECT + "WHERE id = ? AND dispute_id = ?", MAPPER, imageId, disputeId);
    }

    public long countByDisputeId(long disputeId) {
        return Db.one("SELECT COUNT(*) FROM dispute_images WHERE dispute_id = ?",
                rs -> rs.getLong(1), disputeId).orElse(0L);
    }

    public void insert(long disputeId, String fileName, String storedFileName, String contentType, long fileSize) {
        Db.insert(
                "INSERT INTO dispute_images (dispute_id, file_name, stored_file_name, content_type, file_size, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                disputeId, fileName, storedFileName, contentType, fileSize, LocalDateTime.now()
        );
    }
}
