package pl.jollycart.image;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import pl.jollycart.db.Db;
import pl.jollycart.db.RowMapper;

/** Zapytania SQL do tabeli offer_images (odpowiednik OfferImageRepository). */
public final class OfferImageDao {

    private static final String COLUMNS =
            "id, offer_id, file_name, stored_file_name, content_type, file_size, created_at";

    private static final RowMapper<OfferImage> MAPPER = rs -> new OfferImage(
            rs.getLong("id"),
            rs.getLong("offer_id"),
            rs.getString("file_name"),
            rs.getString("stored_file_name"),
            rs.getString("content_type"),
            rs.getLong("file_size"),
            rs.getObject("created_at", LocalDateTime.class)
    );

    public Optional<OfferImage> findById(long id) {
        return Db.one("SELECT " + COLUMNS + " FROM offer_images WHERE id = ?", MAPPER, id);
    }

    public List<OfferImage> findByOfferIdOrderByCreatedAtAsc(long offerId) {
        return Db.list(
                "SELECT " + COLUMNS + " FROM offer_images WHERE offer_id = ? ORDER BY created_at ASC, id ASC",
                MAPPER, offerId
        );
    }

    public long insert(OfferImage image) {
        return Db.insert(
                "INSERT INTO offer_images (offer_id, file_name, stored_file_name, content_type, "
                        + "file_size, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                image.offerId(), image.fileName(), image.storedFileName(), image.contentType(),
                image.fileSize(), image.createdAt()
        );
    }

    public void delete(long id) {
        Db.update("DELETE FROM offer_images WHERE id = ?", id);
    }
}
