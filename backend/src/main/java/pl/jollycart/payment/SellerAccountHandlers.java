package pl.jollycart.payment;

import java.io.IOException;
import java.util.Map;

import pl.jollycart.web.Ctx;
import pl.jollycart.web.Router;

/** Endpointy /api/payments/seller (odpowiednik PaymentController). Wymagaja zalogowania. */
public final class SellerAccountHandlers {

    private final SellerAccountService sellerAccountService;

    public SellerAccountHandlers(SellerAccountService sellerAccountService) {
        this.sellerAccountService = sellerAccountService;
    }

    public void routes(Router router) {
        // Wariant 1: przekierowanie na strone Stripe
        router.post("/api/payments/seller/onboarding", this::startOnboarding);
        router.post("/api/payments/seller/dashboard", this::openDashboard);

        // Wariant 2: formularz Stripe osadzony w aplikacji (Connect embedded components)
        router.post("/api/payments/seller/embedded-session", this::embeddedSession);

        router.get("/api/payments/seller/status", this::status);
    }

    private void startOnboarding(Ctx ctx) throws IOException {
        String url = sellerAccountService.createOnboardingLink(ctx.currentUser());
        ctx.json(200, Map.of("url", url));
    }

    private void openDashboard(Ctx ctx) throws IOException {
        String url = sellerAccountService.createDashboardLink(ctx.currentUser());
        ctx.json(200, Map.of("url", url));
    }

    private void embeddedSession(Ctx ctx) throws IOException {
        String mode = ctx.query("mode");
        ctx.json(200, sellerAccountService.createEmbeddedSession(
                ctx.currentUser(), mode == null ? "onboarding" : mode));
    }

    private void status(Ctx ctx) throws IOException {
        ctx.json(200, sellerAccountService.getStatus(ctx.currentUser()));
    }
}
