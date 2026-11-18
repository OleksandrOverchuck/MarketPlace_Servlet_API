package pl.jollycart.dispute;

import java.io.IOException;

import pl.jollycart.web.Ctx;
import pl.jollycart.web.Router;

/** Endpointy sporow (port OrderController.dispute i StaffDisputeController). Wymagaja zalogowania. */
public final class DisputeHandlers {

    private final DisputeService disputeService;

    public DisputeHandlers(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    public void routes(Router router) {
        // Zgloszenie problemu: tytul + opis + opcjonalne zdjecia (multipart/form-data)
        router.post("/api/orders/{id}/dispute", this::open);
    }

    private void open(Ctx ctx) throws IOException {
        ctx.json(200, disputeService.openDispute(
                ctx.currentUser(),
                ctx.pathLong("id"),
                ctx.query("title"),
                ctx.query("description"),
                ctx.parts("files")
        ));
    }
}
