package pl.jollycart.offer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.LongPredicate;

import pl.jollycart.category.Category;
import pl.jollycart.category.CategoryDao;
import pl.jollycart.db.Db;
import pl.jollycart.user.User;

/** Logika ofert (odpowiednik OfferService). */
public final class OfferService {

    private final OfferDao offerDao;
    private final CategoryDao categoryDao;

    /**
     * Czy sprzedawca ma aktywne konto wyplat (userId -> true/false).
     * W Springu to SellerAccountService.isPayoutReady (pyta Stripe) - podpinamy w etapie platnosci.
     */
    private final LongPredicate payoutReady;

    public OfferService(OfferDao offerDao, CategoryDao categoryDao, LongPredicate payoutReady) {
        this.offerDao = offerDao;
        this.categoryDao = categoryDao;
        this.payoutReady = payoutReady;
    }

    /** Orientacyjny obszar lokalizacji do mapy; wszystkie wartosci null = brak obszaru. */
    private record LocationArea(Double latitude, Double longitude, Integer radiusMeters) {
        static final LocationArea NONE = new LocationArea(null, null, null);
    }

    /*
     * Platnosc przez JollyCart: tylko oferty SALE z cena, a sprzedawca
     * musi miec aktywne konto wyplat (dodane w ustawieniach profilu).
     */
    private boolean resolveOnlinePayment(Boolean requested, OfferType type, BigDecimal price, User seller) {
        if (!Boolean.TRUE.equals(requested)) {
            return false;
        }

        if (type != OfferType.SALE) {
            throw new IllegalArgumentException(
                    "Płatność przez JollyCart jest dostępna tylko dla ofert sprzedaży");
        }

        if (price == null || price.compareTo(new BigDecimal("2.00")) < 0) {
            throw new IllegalArgumentException(
                    "Płatność przez JollyCart wymaga ceny co najmniej 2,00 zł");
        }

        if (!payoutReady.test(seller.id())) {
            throw new IllegalArgumentException(
                    "Aby włączyć płatność przez JollyCart, dodaj konto bankowe w ustawieniach profilu");
        }

        return true;
    }

    /*
     * Obszar lokalizacji zapisujemy tylko wtedy, gdy podano tekst lokalizacji oraz komplet
     * wspolrzednych z poprawnego zakresu. W przeciwnym razie czyscimy go, zeby nie zostaly
     * wspolrzedne po zmianie lub usunieciu lokalizacji.
     */
    private LocationArea locationArea(String location, Double latitude, Double longitude, Integer radius) {
        boolean hasLocationText = location != null && !location.isBlank();

        boolean valid = hasLocationText
                && latitude != null
                && longitude != null
                && Double.isFinite(latitude)
                && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180;

        return valid ? new LocationArea(latitude, longitude, radius) : LocationArea.NONE;
    }

    public OfferResponse createOffer(User user, CreateOfferRequest request) {
        return Db.inTransaction(() -> {
            Category category = categoryDao.findById(request.categoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Kategoria nie została znaleziona"));

            LocationArea area = locationArea(
                    request.location(), request.latitude(), request.longitude(),
                    request.locationRadiusMeters());

            boolean onlinePayment = resolveOnlinePayment(
                    request.onlinePaymentEnabled(), request.type(), request.price(), user);

            // Dostawa ma sens tylko przy platnosci przez JollyCart
            BigDecimal shippingCost = onlinePayment ? request.shippingCost() : null;

            LocalDateTime now = LocalDateTime.now();

            Offer offer = new Offer(
                    null, request.title(), request.description(), request.price(), request.location(),
                    area.latitude(), area.longitude(), area.radiusMeters(),
                    request.type(), OfferStatus.ACTIVE, onlinePayment, shippingCost,
                    user.id(), category.id(), now, now);

            long id = offerDao.insert(offer);

            return OfferResponse.from(withId(offer, id), user, category);
        });
    }

    public OfferResponse getOfferById(long id) {
        return offerDao.findResponseById(id)
                .orElseThrow(() -> new IllegalArgumentException("Oferta nie została znaleziona"));
    }

    public List<OfferResponse> getOffersByUser(long userId) {
        return offerDao.findResponsesByUserId(userId);
    }

    public List<OfferResponse> searchOffers(String search, String location, Long categoryId,
                                            OfferType type, BigDecimal minPrice, BigDecimal maxPrice) {
        return offerDao.search(
                likePattern(search), likePattern(location), categoryId, type, minPrice, maxPrice);
    }

    public long countActiveOffers() {
        return offerDao.countActive();
    }

    public OfferResponse updateOffer(long offerId, User user, UpdateOfferRequest request) {
        return Db.inTransaction(() -> {
            Offer offer = offerDao.findById(offerId)
                    .orElseThrow(() -> new IllegalArgumentException("Oferta nie została znaleziona"));

            if (!offer.userId().equals(user.id())) {
                throw new IllegalArgumentException("Nie możesz edytować cudzej oferty");
            }

            Category category = categoryDao.findById(request.categoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Kategoria nie została znaleziona"));

            // Gdy trwa transakcja (RESERVED), oferta jest zamrozona -
            // inaczej sprzedawca moglby ja zmienic po tym, jak ktos zaplacil.
            if (offer.status() == OfferStatus.RESERVED) {
                throw new IllegalArgumentException(
                        "Oferta jest w trakcie transakcji - nie można jej teraz edytować");
            }

            if (request.status() == OfferStatus.RESERVED) {
                throw new IllegalArgumentException(
                        "Status \"zarezerwowane\" ustawia system podczas płatności");
            }

            // Brak pola w zadaniu = bez zmiany (ale tylko dla ofert SALE).
            boolean onlinePayment = request.onlinePaymentEnabled() == null
                    ? offer.onlinePaymentEnabled() && request.type() == OfferType.SALE
                    : resolveOnlinePayment(
                            request.onlinePaymentEnabled(), request.type(), request.price(), user);

            LocationArea area = locationArea(
                    request.location(), request.latitude(), request.longitude(),
                    request.locationRadiusMeters());

            // Brak pola w zadaniu = bez zmiany; bez platnosci online dostawa = brak
            BigDecimal shippingCost;
            if (!onlinePayment) {
                shippingCost = null;
            } else if (request.shippingCost() != null) {
                shippingCost = request.shippingCost();
            } else {
                shippingCost = offer.shippingCost();
            }

            Offer updated = new Offer(
                    offer.id(), request.title(), request.description(), request.price(), request.location(),
                    area.latitude(), area.longitude(), area.radiusMeters(),
                    request.type(), request.status(), onlinePayment, shippingCost,
                    offer.userId(), category.id(), offer.createdAt(), LocalDateTime.now());

            offerDao.update(updated);

            return OfferResponse.from(updated, user, category);
        });
    }

    /* ------------------------------------------------------------------ */

    /** "%tekst%" malymi literami do LIKE; pusty tekst = brak filtra. */
    private static String likePattern(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return "%" + value.trim().toLowerCase() + "%";
    }

    private static Offer withId(Offer o, long id) {
        return new Offer(id, o.title(), o.description(), o.price(), o.location(),
                o.locationLatitude(), o.locationLongitude(), o.locationRadiusMeters(),
                o.type(), o.status(), o.onlinePaymentEnabled(), o.shippingCost(),
                o.userId(), o.categoryId(), o.createdAt(), o.updatedAt());
    }
}
