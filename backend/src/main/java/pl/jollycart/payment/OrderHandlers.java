package pl.jollycart.payment;

import java.io.IOException;

import pl.jollycart.web.Ctx;
import pl.jollycart.web.Router;

/** Endpointy /api/orders (odpowiednik OrderController). Wymagaja zalogowania. */
public final class OrderHandlers {

    private final OrderService orderService;

    public OrderHandlers(OrderService orderService) {
        this.orderService = orderService;
    }

    public void routes(Router router) {
        // Wycena do tabeli kosztow w oknie "Dane do wysylki" (tylko odczyt)
        router.get("/api/orders/quote", this::quote);

        // Listy "Moje zakupy" / "Moja sprzedaz" oraz czerwona kropka
        router.get("/api/orders/purchases", this::purchases);
        router.get("/api/orders/sales", this::sales);
        router.get("/api/orders/unread-count", this::unreadCount);
        router.post("/api/orders/mark-seen", this::markSeen);

        // Start platnosci: adres dostawy -> zamowienie + adres strony platnosci Stripe
        router.post("/api/orders/checkout", this::checkout);

        // Kupujacy rezygnuje z platnosci / front pyta Stripe, czy juz zaplacono (gdy webhook jeszcze nie dotarl)
        router.post("/api/orders/{id}/cancel", this::cancel);
        router.post("/api/orders/{id}/sync", this::sync);
    }

    private void checkout(Ctx ctx) throws IOException {
        CheckoutRequest req = ctx.body(CheckoutRequest.class);

        ctx.json(200, orderService.startCheckout(
                ctx.currentUser(),
                req.offerId(),
                req.city(),
                req.street(),
                req.postalCode(),
                req.phone()
        ));
    }

    private void purchases(Ctx ctx) throws IOException {
        ctx.json(200, orderService.getPurchases(ctx.currentUser()));
    }

    private void sales(Ctx ctx) throws IOException {
        ctx.json(200, orderService.getSales(ctx.currentUser()));
    }

    private void unreadCount(Ctx ctx) throws IOException {
        ctx.json(200, orderService.countUnread(ctx.currentUser()));
    }

    private void markSeen(Ctx ctx) {
        orderService.markSeen(ctx.currentUser());
        ctx.noContent();
    }

    private void cancel(Ctx ctx) throws IOException {
        ctx.json(200, orderService.cancelPending(ctx.currentUser(), ctx.pathLong("id")));
    }

    private void sync(Ctx ctx) throws IOException {
        ctx.json(200, orderService.syncPending(ctx.currentUser(), ctx.pathLong("id")));
    }

    private void quote(Ctx ctx) throws IOException {
        String offerId = ctx.query("offerId");

        if (offerId == null || offerId.isBlank()) {
            throw new IllegalArgumentException("Brak oferty");
        }

        try {
            ctx.json(200, orderService.quote(Long.parseLong(offerId.trim())));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Nieprawidłowy identyfikator oferty");
        }
    }
}
