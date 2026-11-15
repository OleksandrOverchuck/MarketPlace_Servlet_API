package pl.jollycart.offer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import pl.jollycart.category.Category;
import pl.jollycart.user.User;

/** JSON oferty zwracany frontendowi - identyczny z OfferResponse w wersji Spring Boot. */
public record OfferResponse(
        Long id,
        String title,
        String description,
        BigDecimal price,
        String location,
        OfferType type,
        OfferStatus status,
        Long userId,
        String nickname,
        String sellerPhone,
        Long categoryId,
        String categoryName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean onlinePaymentEnabled,
        Double latitude,
        Double longitude,
        Integer locationRadiusMeters,
        BigDecimal shippingCost
) {

    public static OfferResponse from(Offer offer, User seller, Category category) {
        return new OfferResponse(
                offer.id(),
                offer.title(),
                offer.description(),
                offer.price(),
                offer.location(),
                offer.type(),
                offer.status(),
                seller.id(),
                seller.nickname(),
                seller.phone(),
                category.id(),
                category.name(),
                offer.createdAt(),
                offer.updatedAt(),
                offer.onlinePaymentEnabled(),
                offer.locationLatitude(),
                offer.locationLongitude(),
                offer.locationRadiusMeters(),
                offer.shippingCost()
        );
    }
}
