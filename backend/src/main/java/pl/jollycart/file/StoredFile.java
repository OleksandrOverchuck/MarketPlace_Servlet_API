package pl.jollycart.file;

import java.time.LocalDateTime;

/** Wiersz tabeli stored_files (metadane pliku; sam plik lezy w katalogu uploads). */
public record StoredFile(
        Long id,
        String originalFileName,
        String storedFileName,
        String contentType,
        Long fileSize,
        LocalDateTime createdAt
) {
}
