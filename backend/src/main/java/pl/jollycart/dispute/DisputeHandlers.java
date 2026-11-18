package pl.jollycart.dispute;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

import pl.jollycart.web.Ctx;
import pl.jollycart.web.Router;

/** Endpointy sporow (port OrderController.dispute i StaffDisputeController). Wymagaja zalogowania. */
public final class DisputeHandlers {

    public record ResolveRequest(DisputeResolution resolution, String note) {
    }

    private static final Pattern MEDIA_TYPE = Pattern.compile("^[\\w.+-]+/[\\w.+-]+$");

    private final DisputeService disputeService;

    public DisputeHandlers(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    public void routes(Router router) {
        // Zgloszenie problemu: tytul + opis + opcjonalne zdjecia (multipart/form-data)
        router.post("/api/orders/{id}/dispute", this::open);

        // Zakladka "Zgloszenia" w panelu - /api/moderation/** maja MODERATOR i ADMIN (AuthorizationFilter).
        // "open-count" musi byc przed "{id}": router bierze pierwsze pasujace.
        router.get("/api/moderation/disputes", this::list);
        router.get("/api/moderation/disputes/open-count", this::openCount);
        router.get("/api/moderation/disputes/{id}", this::detail);
        router.get("/api/moderation/disputes/{id}/images/{imageId}", this::image);
        router.post("/api/moderation/disputes/{id}/resolve", this::resolve);
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

    // ?status=OPEN (domyslnie, "Aktualne") albo ?status=RESOLVED ("Rozwiazane")
    private void list(Ctx ctx) throws IOException {
        String status = ctx.query("status");
        DisputeStatus parsed;

        try {
            parsed = status == null ? DisputeStatus.OPEN : DisputeStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Nieprawidłowy status zgłoszeń");
        }

        ctx.json(200, disputeService.list(parsed));
    }

    // Liczba aktualnych zgloszen (plakietka przy zakladce)
    private void openCount(Ctx ctx) throws IOException {
        ctx.json(200, Map.of("count", disputeService.countOpen()));
    }

    private void detail(Ctx ctx) throws IOException {
        ctx.json(200, disputeService.getDetail(ctx.pathLong("id")));
    }

    private void image(Ctx ctx) throws IOException {
        DisputeImage image = disputeService.getImage(ctx.pathLong("id"), ctx.pathLong("imageId"));

        Path file;
        try {
            file = disputeService.imagePath(image);
        } catch (IllegalArgumentException e) {
            // wiersz w bazie jest, ale pliku na dysku nie ma
            ctx.response().setStatus(404);
            return;
        }

        String contentType = image.contentType() != null && MEDIA_TYPE.matcher(image.contentType()).matches()
                ? image.contentType()
                : "application/octet-stream";

        // Dowody w sporze nie powinny zostawac w pamieci podrecznej przegladarki
        ctx.response().setHeader("Cache-Control", "no-store");
        ctx.sendFile(file, contentType, image.fileName());
    }

    private void resolve(Ctx ctx) throws IOException {
        ResolveRequest request = ctx.body(ResolveRequest.class);

        ctx.json(200, disputeService.resolve(
                ctx.currentUser(),
                ctx.pathLong("id"),
                request.resolution(),
                request.note()
        ));
    }
}
