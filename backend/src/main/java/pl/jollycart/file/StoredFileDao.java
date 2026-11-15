package pl.jollycart.file;

import java.time.LocalDateTime;
import java.util.Optional;

import pl.jollycart.db.Db;
import pl.jollycart.db.RowMapper;

/** Zapytania SQL do tabeli stored_files (odpowiednik StoredFileRepository). */
public final class StoredFileDao {

    private static final RowMapper<StoredFile> MAPPER = rs -> new StoredFile(
            rs.getLong("id"),
            rs.getString("original_file_name"),
            rs.getString("stored_file_name"),
            rs.getString("content_type"),
            rs.getLong("file_size"),
            rs.getObject("created_at", LocalDateTime.class)
    );

    public Optional<StoredFile> findById(long id) {
        return Db.one(
                "SELECT id, original_file_name, stored_file_name, content_type, file_size, created_at "
                        + "FROM stored_files WHERE id = ?",
                MAPPER, id
        );
    }

    /** Zapisuje metadane pliku i zwraca je razem z nadanym id. */
    public StoredFile insert(String originalFileName, String storedFileName, String contentType, long fileSize) {
        LocalDateTime now = LocalDateTime.now();

        long id = Db.insert(
                "INSERT INTO stored_files (original_file_name, stored_file_name, content_type, "
                        + "file_size, created_at) VALUES (?, ?, ?, ?, ?)",
                originalFileName, storedFileName, contentType, fileSize, now
        );

        return new StoredFile(id, originalFileName, storedFileName, contentType, fileSize, now);
    }
}
