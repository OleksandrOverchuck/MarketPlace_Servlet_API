package pl.jollycart.rating;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import pl.jollycart.web.Ctx;
import pl.jollycart.web.RequestValidationException;
import pl.jollycart.web.Router;

/** Endpointy profilu i ocen pod /api/users/{id} (odpowiednik UserProfileController). */
public final class UserProfileHandlers {

    private final UserRatingService ratingService;

    public UserProfileHandlers(UserRatingService ratingService) {
        this.ratingService = ratingService;
    }

    public void routes(Router router) {
        router.get("/api/users/{id}/profile", this::getProfile);
        router.put("/api/users/{id}/rating", this::rate);
        router.delete("/api/users/{id}/rating", this::removeRating);
    }

    /** Publiczny profil - dostepny takze bez logowania (currentUser wtedy null). */
    private void getProfile(Ctx ctx) throws IOException {
        ctx.json(200, ratingService.getProfile(ctx.pathLong("id"), ctx.currentUser()));
    }

    private void rate(Ctx ctx) throws IOException {
        long id = ctx.pathLong("id");

        RateUserRequest request;
        try {
            request = ctx.body(RateUserRequest.class);
        } catch (RequestValidationException e) {
            // oprocz bledow pol dokladamy ogolny komunikat (jak w Springu)
            Map<String, String> errors = new LinkedHashMap<>(e.getErrors());
            errors.putIfAbsent("message", "Ocena musi być od 1 do 5");
            throw new RequestValidationException(errors);
        }

        ctx.json(200, ratingService.rate(id, ctx.currentUser(), request.stars()));
    }

    private void removeRating(Ctx ctx) throws IOException {
        ctx.json(200, ratingService.removeRating(ctx.pathLong("id"), ctx.currentUser()));
    }
}
