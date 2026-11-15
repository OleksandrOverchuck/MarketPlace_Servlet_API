package pl.jollycart.rating;

import java.time.LocalDateTime;
import java.util.List;

import pl.jollycart.offer.OfferResponse;

/** Publiczny profil uzytkownika (bez e-maila i telefonu). */
public record UserProfileResponse(
        Long id,
        String nickname,
        String avatarUrl,
        String location,
        LocalDateTime memberSince,
        double averageRating,
        long ratingCount,
        Integer myRating,
        boolean self,
        List<OfferResponse> offers
) {
}
