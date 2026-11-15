package pl.jollycart.offer;

import java.io.IOException;
import java.math.BigDecimal;

import pl.jollycart.web.Ctx;
import pl.jollycart.web.Router;

/** Endpointy /api/offers (odpowiednik OfferController). */
public final class OfferHandlers {

    private final OfferService offerService;

    public OfferHandlers(OfferService offerService) {
        this.offerService = offerService;
    }

    public void routes(Router router) {
        router.post("/api/offers", this::create);
        router.get("/api/offers", this::search);
        // stale sciezki (/me, /count) musza byc przed /{id}
        router.get("/api/offers/me", this::getMine);
        router.get("/api/offers/count", this::countActive);
        router.get("/api/offers/{id}", this::getOne);
        router.put("/api/offers/{id}", this::update);
    }

    private void create(Ctx ctx) throws IOException {
        CreateOfferRequest request = ctx.body(CreateOfferRequest.class);
        ctx.json(201, offerService.createOffer(ctx.currentUser(), request));
    }

    private void search(Ctx ctx) throws IOException {
        ctx.json(200, offerService.searchOffers(
                ctx.query("search"),
                ctx.query("location"),
                parseLong(ctx.query("categoryId"), "categoryId"),
                parseType(ctx.query("type")),
                parseDecimal(ctx.query("minPrice"), "minPrice"),
                parseDecimal(ctx.query("maxPrice"), "maxPrice")
        ));
    }

    private void getMine(Ctx ctx) throws IOException {
        ctx.json(200, offerService.getOffersByUser(ctx.currentUser().id()));
    }

    private void countActive(Ctx ctx) throws IOException {
        ctx.json(200, offerService.countActiveOffers());
    }

    private void getOne(Ctx ctx) throws IOException {
        ctx.json(200, offerService.getOfferById(ctx.pathLong("id")));
    }

    private void update(Ctx ctx) throws IOException {
        long id = ctx.pathLong("id");
        UpdateOfferRequest request = ctx.body(UpdateOfferRequest.class);
        ctx.json(200, offerService.updateOffer(id, ctx.currentUser(), request));
    }

    /* ---- parametry zapytania: zla wartosc to blad 400 ---- */

    private static Long parseLong(String value, String name) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Nieprawidłowa wartość parametru " + name);
        }
    }

    private static BigDecimal parseDecimal(String value, String name) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Nieprawidłowa wartość parametru " + name);
        }
    }

    private static OfferType parseType(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OfferType.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Nieprawidłowa wartość parametru type");
        }
    }
}
