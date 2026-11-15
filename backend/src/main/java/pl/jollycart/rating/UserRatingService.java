package pl.jollycart.rating;

import java.util.List;
import java.util.Optional;

import pl.jollycart.db.Db;
import pl.jollycart.offer.OfferDao;
import pl.jollycart.offer.OfferResponse;
import pl.jollycart.user.User;
import pl.jollycart.user.UserDao;
import pl.jollycart.web.StatusException;

/** Oceny uzytkownikow i profil publiczny (odpowiednik UserRatingService). */
public final class UserRatingService {

    private final UserRatingDao ratingDao;
    private final UserDao userDao;
    private final OfferDao offerDao;

    public UserRatingService(UserRatingDao ratingDao, UserDao userDao, OfferDao offerDao) {
        this.ratingDao = ratingDao;
        this.userDao = userDao;
        this.offerDao = offerDao;
    }

    /** viewer moze byc null (niezalogowany). */
    public UserProfileResponse getProfile(long userId, User viewer) {
        User user = findUser(userId);
        RatingSummaryResponse summary = summary(user.id(), viewer);

        List<OfferResponse> offers = offerDao.findActiveResponsesByUserId(user.id());

        return new UserProfileResponse(
                user.id(),
                user.nickname(),
                user.avatarUrl(),
                user.location(),
                user.createdAt(),
                summary.averageRating(),
                summary.ratingCount(),
                summary.myRating(),
                viewer != null && viewer.id().equals(user.id()),
                offers
        );
    }

    public RatingSummaryResponse rate(long userId, User rater, int stars) {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Ocena musi być od 1 do 5");
        }

        return Db.inTransaction(() -> {
            User rated = findUser(userId);

            if (rater == null) {
                throw new StatusException(401, "");
            }

            if (rater.id().equals(rated.id())) {
                throw new IllegalArgumentException("Nie możesz ocenić samego siebie");
            }

            // Jedna ocena na pare (oceniajacy, oceniany): jest -> aktualizujemy, nie ma -> dodajemy
            Optional<UserRatingDao.Rating> existing = ratingDao.findByRaterAndRated(rater.id(), rated.id());

            if (existing.isPresent()) {
                ratingDao.updateStars(existing.get().id(), stars);
            } else {
                ratingDao.insert(rater.id(), rated.id(), stars);
            }

            return summary(rated.id(), rater);
        });
    }

    public RatingSummaryResponse removeRating(long userId, User rater) {
        return Db.inTransaction(() -> {
            User rated = findUser(userId);

            if (rater == null) {
                throw new StatusException(401, "");
            }

            ratingDao.delete(rater.id(), rated.id());

            return summary(rated.id(), rater);
        });
    }

    private RatingSummaryResponse summary(long ratedUserId, User viewer) {
        double average = ratingDao.averageStars(ratedUserId);
        long count = ratingDao.countByRatedUser(ratedUserId);

        Integer mine = null;
        if (viewer != null) {
            mine = ratingDao.findByRaterAndRated(viewer.id(), ratedUserId)
                    .map(UserRatingDao.Rating::stars)
                    .orElse(null);
        }

        double rounded = Math.round(average * 10.0) / 10.0;
        return new RatingSummaryResponse(rounded, count, mine);
    }

    /** Zablokowanego uzytkownika traktujemy jak nieistniejacego. */
    private User findUser(long id) {
        User user = userDao.findById(id)
                .orElseThrow(() -> new StatusException(404, "Użytkownik nie został znaleziony"));

        if (user.isBanned()) {
            throw new StatusException(404, "Użytkownik nie został znaleziony");
        }

        return user;
    }
}
