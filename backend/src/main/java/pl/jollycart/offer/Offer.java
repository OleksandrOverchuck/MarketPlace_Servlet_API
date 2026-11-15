package pl.jollycart.offer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Wiersz tabeli offers (same kolumny; autor i kategoria jako id). */
public record Offer(
        Long id,
        String title,
        String description,
        BigDecimal price,
        String location,
        Double locationLatitude,
        Double locationLongitude,
        Integer locationRadiusMeters,
        OfferType type,
        OfferStatus status,
        boolean onlinePaymentEnabled,
        BigDecimal shippingCost,
        Long userId,
        Long categoryId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
