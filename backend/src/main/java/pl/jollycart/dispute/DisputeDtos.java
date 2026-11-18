package pl.jollycart.dispute;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import pl.jollycart.payment.OrderStatus;

/* Odpowiedzi API dla panelu zgloszen (admin / moderator). */
public final class DisputeDtos {

    private DisputeDtos() {
    }

    // Wiersz na liscie "Aktualne" / "Rozwiazane"
    public record DisputeSummary(
            Long id,
            Long orderId,
            Long offerId,
            String offerTitle,
            String title,
            DisputeStatus status,
            DisputeResolution resolution,
            String buyerNickname,
            String sellerNickname,
            int imageCount,
            LocalDateTime createdAt,
            LocalDateTime resolvedAt,
            String resolverNickname
    ) {
    }

    public record DisputeImageInfo(
            Long id,
            String fileName,
            String contentType,
            Long fileSize
    ) {
    }

    // Pelne zgloszenie po kliknieciu
    public record DisputeDetail(
            Long id,
            Long orderId,
            Long offerId,
            String offerTitle,
            String title,
            String description,
            DisputeStatus status,
            DisputeResolution resolution,
            String resolverNote,
            String resolverNickname,
            LocalDateTime createdAt,
            LocalDateTime resolvedAt,
            Long buyerId,
            String buyerNickname,
            Long sellerId,
            String sellerNickname,
            OrderStatus orderStatus,
            BigDecimal totalAmount,
            BigDecimal sellerPayout,
            String currency,
            List<DisputeImageInfo> images
    ) {
    }
}
