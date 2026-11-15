package pl.jollycart.rating;

public record RatingSummaryResponse(double averageRating, long ratingCount, Integer myRating) {
}
