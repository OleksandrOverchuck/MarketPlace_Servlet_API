package pl.jollycart.image;

import java.time.LocalDateTime;

/** Wiersz tabeli offer_images (metadane zdjecia; sam plik lezy w katalogu uploads). */
public record OfferImage(
        Long id,
        Long offerId,
        String fileName,
        String storedFileName,
        String contentType,
        Long fileSize,
        LocalDateTime createdAt
) {
}
