package pl.jollycart.dispute;

import java.time.LocalDateTime;

/** Wiersz tabeli dispute_images: zdjecie dolaczone do zgloszenia. */
public record DisputeImage(
        Long id,
        Long disputeId,
        String fileName,
        String storedFileName,
        String contentType,
        Long fileSize,
        LocalDateTime createdAt
) {
}
