package pl.jollycart.offer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import pl.jollycart.db.Db;
import pl.jollycart.db.RowMapper;
import pl.jollycart.user.AccountStatus;

/** Zapytania SQL do tabeli offers (odpowiednik OfferRepository + Specification). */
public final class OfferDao {

    private static final String OFFER_COLUMNS =
            "id, title, description, price, location, location_latitude, location_longitude, "
            + "location_radius_meters, type, status, online_payment_enabled, shipping_cost, "
            + "user_id, category_id, created_at, updated_at";

    /** Oferta razem z autorem i kategoria - jednym zapytaniem (JOIN), bez doczytywania po kolei. */
    private static final String RESPONSE_SELECT =
            "SELECT o.id, o.title, o.description, o.price, o.location, o.type, o.status, "
            + "o.user_id, u.nickname, u.phone, o.category_id, c.name AS category_name, "
            + "o.created_at, o.updated_at, o.online_payment_enabled, o.location_latitude, "
            + "o.location_longitude, o.location_radius_meters, o.shipping_cost "
            + "FROM offers o "
            + "JOIN users u ON u.id = o.user_id "
            + "JOIN categories c ON c.id = o.category_id ";

    private static final RowMapper<Offer> OFFER_MAPPER = rs -> new Offer(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getObject("price", BigDecimal.class),
            rs.getString("location"),
            rs.getObject("location_latitude", Double.class),
            rs.getObject("location_longitude", Double.class),
            rs.getObject("location_radius_meters", Integer.class),
            OfferType.valueOf(rs.getString("type")),
            OfferStatus.valueOf(rs.getString("status")),
            rs.getBoolean("online_payment_enabled"),
            rs.getObject("shipping_cost", BigDecimal.class),
            rs.getLong("user_id"),
            rs.getLong("category_id"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class)
    );

    private static final RowMapper<OfferResponse> RESPONSE_MAPPER = rs -> new OfferResponse(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getObject("price", BigDecimal.class),
            rs.getString("location"),
            OfferType.valueOf(rs.getString("type")),
            OfferStatus.valueOf(rs.getString("status")),
            rs.getLong("user_id"),
            rs.getString("nickname"),
            rs.getString("phone"),
            rs.getLong("category_id"),
            rs.getString("category_name"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class),
            rs.getBoolean("online_payment_enabled"),
            rs.getObject("location_latitude", Double.class),
            rs.getObject("location_longitude", Double.class),
            rs.getObject("location_radius_meters", Integer.class),
            rs.getObject("shipping_cost", BigDecimal.class)
    );

    public Optional<Offer> findById(long id) {
        return Db.one("SELECT " + OFFER_COLUMNS + " FROM offers WHERE id = ?", OFFER_MAPPER, id);
    }

    public boolean existsById(long id) {
        return Db.exists("SELECT 1 FROM offers WHERE id = ? LIMIT 1", id);
    }

    public Optional<OfferResponse> findResponseById(long id) {
        return Db.one(RESPONSE_SELECT + "WHERE o.id = ?", RESPONSE_MAPPER, id);
    }

    public List<OfferResponse> findResponsesByUserId(long userId) {
        return Db.list(
                RESPONSE_SELECT + "WHERE o.user_id = ? ORDER BY o.created_at DESC, o.id DESC",
                RESPONSE_MAPPER, userId
        );
    }

    /**
     * Wyszukiwanie: zawsze tylko ACTIVE oferty ACTIVE (niezablokowanych) uzytkownikow;
     * kazdy z pozostalych filtrow jest opcjonalny (null = pomijamy). Najnowsze na poczatku.
     */
    public List<OfferResponse> search(String searchPattern, String locationPattern, Long categoryId,
                                      OfferType type, BigDecimal minPrice, BigDecimal maxPrice) {
        StringBuilder sql = new StringBuilder(RESPONSE_SELECT)
                .append("WHERE o.status = ? AND u.status = ? ");
        List<Object> params = new ArrayList<>();
        params.add(OfferStatus.ACTIVE);
        params.add(AccountStatus.ACTIVE);

        if (searchPattern != null) {
            sql.append("AND (LOWER(o.title) LIKE ? OR LOWER(o.description) LIKE ?) ");
            params.add(searchPattern);
            params.add(searchPattern);
        }
        if (locationPattern != null) {
            sql.append("AND LOWER(o.location) LIKE ? ");
            params.add(locationPattern);
        }
        if (categoryId != null) {
            sql.append("AND o.category_id = ? ");
            params.add(categoryId);
        }
        if (type != null) {
            sql.append("AND o.type = ? ");
            params.add(type);
        }
        if (minPrice != null) {
            sql.append("AND o.price >= ? ");
            params.add(minPrice);
        }
        if (maxPrice != null) {
            sql.append("AND o.price <= ? ");
            params.add(maxPrice);
        }

        sql.append("ORDER BY o.created_at DESC, o.id DESC");

        return Db.list(sql.toString(), RESPONSE_MAPPER, params.toArray());
    }

    public long countActive() {
        return Db.one(
                "SELECT COUNT(*) FROM offers o JOIN users u ON u.id = o.user_id "
                        + "WHERE o.status = ? AND u.status = ?",
                rs -> rs.getLong(1),
                OfferStatus.ACTIVE, AccountStatus.ACTIVE
        ).orElse(0L);
    }

    public long insert(Offer offer) {
        return Db.insert(
                "INSERT INTO offers (title, description, price, location, location_latitude, "
                        + "location_longitude, location_radius_meters, type, status, "
                        + "online_payment_enabled, shipping_cost, user_id, category_id, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                offer.title(), offer.description(), offer.price(), offer.location(),
                offer.locationLatitude(), offer.locationLongitude(), offer.locationRadiusMeters(),
                offer.type(), offer.status(), offer.onlinePaymentEnabled(), offer.shippingCost(),
                offer.userId(), offer.categoryId(), offer.createdAt(), offer.updatedAt()
        );
    }

    public void update(Offer offer) {
        Db.update(
                "UPDATE offers SET title = ?, description = ?, price = ?, location = ?, "
                        + "location_latitude = ?, location_longitude = ?, location_radius_meters = ?, "
                        + "type = ?, status = ?, category_id = ?, online_payment_enabled = ?, "
                        + "shipping_cost = ?, updated_at = ? WHERE id = ?",
                offer.title(), offer.description(), offer.price(), offer.location(),
                offer.locationLatitude(), offer.locationLongitude(), offer.locationRadiusMeters(),
                offer.type(), offer.status(), offer.categoryId(), offer.onlinePaymentEnabled(),
                offer.shippingCost(), offer.updatedAt(), offer.id()
        );
    }
}
