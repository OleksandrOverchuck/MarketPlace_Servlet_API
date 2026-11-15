package pl.jollycart.image;

import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Pattern;

import jakarta.servlet.http.Part;

import pl.jollycart.file.FileService;
import pl.jollycart.web.Ctx;
import pl.jollycart.web.Router;

/** Endpointy zdjec ofert pod /api/offers (odpowiednik OfferImageController). */
public final class OfferImageHandlers {

    private static final Pattern MEDIA_TYPE = Pattern.compile("^[\\w.+-]+/[\\w.+-]+$");

    private final OfferImageService offerImageService;
    private final FileService fileService;

    public OfferImageHandlers(OfferImageService offerImageService, FileService fileService) {
        this.offerImageService = offerImageService;
        this.fileService = fileService;
    }

    public void routes(Router router) {
        router.post("/api/offers/{offerId}/images", this::upload);
        router.get("/api/offers/{offerId}/images", this::list);
        router.get("/api/offers/images/{imageId}", this::getImage);
        router.delete("/api/offers/images/{imageId}", this::delete);
    }

    private void upload(Ctx ctx) throws IOException {
        long offerId = ctx.pathLong("offerId");
        Part file = ctx.part("file");
        ctx.json(200, offerImageService.uploadImage(offerId, ctx.currentUser(), file));
    }

    private void list(Ctx ctx) throws IOException {
        ctx.json(200, offerImageService.getImagesByOfferId(ctx.pathLong("offerId")));
    }

    private void getImage(Ctx ctx) throws IOException {
        OfferImage image = offerImageService.getImageById(ctx.pathLong("imageId"));

        Path file;
        try {
            file = fileService.loadFile(image.storedFileName());
        } catch (IllegalArgumentException e) {
            // wiersz w bazie jest, ale pliku na dysku nie ma
            ctx.response().setStatus(404);
            return;
        }

        String contentType = image.contentType() != null && MEDIA_TYPE.matcher(image.contentType()).matches()
                ? image.contentType()
                : "application/octet-stream";

        ctx.sendFile(file, contentType, image.fileName());
    }

    private void delete(Ctx ctx) {
        offerImageService.deleteImage(ctx.pathLong("imageId"), ctx.currentUser());
        ctx.noContent();
    }
}
