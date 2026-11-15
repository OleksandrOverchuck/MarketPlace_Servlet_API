package pl.jollycart.admin;

import java.io.IOException;

import pl.jollycart.offer.OfferStatus;
import pl.jollycart.user.AccountStatus;
import pl.jollycart.user.Role;
import pl.jollycart.web.Ctx;
import pl.jollycart.web.QueryParams;
import pl.jollycart.web.Router;

/** Endpointy /api/moderation (dostep dla MODERATOR i ADMIN - zob. AuthorizationFilter). */
public final class ModerationHandlers {

    private final ModerationService moderationService;

    public ModerationHandlers(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    public void routes(Router router) {
        router.get("/api/moderation/users", this::searchUsers);
        router.get("/api/moderation/users/{id}", this::getUser);
        router.post("/api/moderation/users/{id}/ban", this::banUser);
        router.post("/api/moderation/users/{id}/unban", this::unbanUser);
        router.get("/api/moderation/offers", this::searchOffers);
        router.delete("/api/moderation/offers/{id}", this::deleteOffer);
    }

    private void searchUsers(Ctx ctx) throws IOException {
        ctx.json(200, moderationService.searchUsers(
                ctx.query("q"),
                QueryParams.enumParam(ctx, "role", Role.class),
                QueryParams.enumParam(ctx, "status", AccountStatus.class),
                QueryParams.intParam(ctx, "page", 0),
                QueryParams.intParam(ctx, "size", 20)));
    }

    private void getUser(Ctx ctx) throws IOException {
        ctx.json(200, moderationService.getUser(ctx.pathLong("id")));
    }

    private void banUser(Ctx ctx) throws IOException {
        long id = ctx.pathLong("id");
        BanUserRequest request = ctx.bodyWithMessage(BanUserRequest.class);
        ctx.json(200, moderationService.banUser(ctx.currentUser(), id, request.reason()));
    }

    private void unbanUser(Ctx ctx) throws IOException {
        ctx.json(200, moderationService.unbanUser(ctx.currentUser(), ctx.pathLong("id")));
    }

    private void searchOffers(Ctx ctx) throws IOException {
        ctx.json(200, moderationService.searchOffers(
                ctx.query("q"),
                QueryParams.longParam(ctx, "userId"),
                QueryParams.enumParam(ctx, "status", OfferStatus.class),
                QueryParams.intParam(ctx, "page", 0),
                QueryParams.intParam(ctx, "size", 20)));
    }

    private void deleteOffer(Ctx ctx) {
        moderationService.deleteOffer(ctx.currentUser(), ctx.pathLong("id"), ctx.query("reason"));
        ctx.noContent();
    }
}
