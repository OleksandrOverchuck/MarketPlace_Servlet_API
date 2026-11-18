package pl.jollycart.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import pl.jollycart.db.Db;
import pl.jollycart.db.RowMapper;

/** Zapytania SQL do tabeli orders (odpowiednik OrderRepository). */
public final class OrderDao {

    /** Kolumny bez id - w tej samej kolejnosci co wartosci w values(). Z nich budujemy INSERT i UPDATE. */
    private static final String[] COLUMNS = {
            "offer_id", "offer_title", "buyer_id", "seller_id",
            "amount", "shipping_cost", "service_fee", "payment_fee", "total_amount",
            "currency", "status",
            "stripe_session_id", "stripe_checkout_url", "stripe_payment_intent_id",
            "stripe_charge_id", "stripe_transfer_id",
            "checkout_expires_at", "paid_at", "ship_deadline", "shipped_at",
            "delivered_at", "confirm_deadline", "completed_at",
            "dispute_reason",
            "shipping_city", "shipping_street", "shipping_postal_code", "shipping_phone",
            "buyer_unread", "seller_unread",
            "created_at", "updated_at"
    };

    private static final String SELECT = "SELECT id, " + String.join(", ", COLUMNS) + " FROM orders ";

    private static final RowMapper<PurchaseOrder> MAPPER = rs -> {
        PurchaseOrder o = new PurchaseOrder();
        o.setId(rs.getLong("id"));
        o.setOfferId(rs.getLong("offer_id"));
        o.setOfferTitle(rs.getString("offer_title"));
        o.setBuyerId(rs.getLong("buyer_id"));
        o.setSellerId(rs.getLong("seller_id"));
        o.setAmount(rs.getObject("amount", BigDecimal.class));
        o.setShippingCost(rs.getObject("shipping_cost", BigDecimal.class));
        o.setServiceFee(rs.getObject("service_fee", BigDecimal.class));
        o.setPaymentFee(rs.getObject("payment_fee", BigDecimal.class));
        o.setTotalAmount(rs.getObject("total_amount", BigDecimal.class));
        o.setCurrency(rs.getString("currency"));
        o.setStatus(OrderStatus.valueOf(rs.getString("status")));
        o.setStripeSessionId(rs.getString("stripe_session_id"));
        o.setStripeCheckoutUrl(rs.getString("stripe_checkout_url"));
        o.setStripePaymentIntentId(rs.getString("stripe_payment_intent_id"));
        o.setStripeChargeId(rs.getString("stripe_charge_id"));
        o.setStripeTransferId(rs.getString("stripe_transfer_id"));
        o.setCheckoutExpiresAt(rs.getObject("checkout_expires_at", LocalDateTime.class));
        o.setPaidAt(rs.getObject("paid_at", LocalDateTime.class));
        o.setShipDeadline(rs.getObject("ship_deadline", LocalDateTime.class));
        o.setShippedAt(rs.getObject("shipped_at", LocalDateTime.class));
        o.setDeliveredAt(rs.getObject("delivered_at", LocalDateTime.class));
        o.setConfirmDeadline(rs.getObject("confirm_deadline", LocalDateTime.class));
        o.setCompletedAt(rs.getObject("completed_at", LocalDateTime.class));
        o.setDisputeReason(rs.getString("dispute_reason"));
        o.setShippingCity(rs.getString("shipping_city"));
        o.setShippingStreet(rs.getString("shipping_street"));
        o.setShippingPostalCode(rs.getString("shipping_postal_code"));
        o.setShippingPhone(rs.getString("shipping_phone"));
        o.setBuyerUnread(rs.getBoolean("buyer_unread"));
        o.setSellerUnread(rs.getBoolean("seller_unread"));
        o.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        o.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        return o;
    };

    private static final RowMapper<Long> ID_MAPPER = rs -> rs.getLong(1);

    /* ------------------------------------------------------------------
     * Odczyt pojedynczego zamowienia
     * ------------------------------------------------------------------ */

    public Optional<PurchaseOrder> findById(long id) {
        return Db.one(SELECT + "WHERE id = ?", MAPPER, id);
    }

    /**
     * Blokada wiersza (SELECT ... FOR UPDATE): webhook, scheduler i kupujacy nie zmienia
     * tego samego zamowienia jednoczesnie. Blokada trwa do konca transakcji, wiec
     * wywoluj to wewnatrz Db.inTransaction(...) - poza transakcja nie ma sensu.
     */
    public Optional<PurchaseOrder> findByIdForUpdate(long id) {
        return Db.one(SELECT + "WHERE id = ? FOR UPDATE", MAPPER, id);
    }

    public Optional<PurchaseOrder> findBySessionIdForUpdate(String sessionId) {
        return Db.one(SELECT + "WHERE stripe_session_id = ? FOR UPDATE", MAPPER, sessionId);
    }

    /* ------------------------------------------------------------------
     * Listy
     * ------------------------------------------------------------------ */

    public List<PurchaseOrder> findByBuyerId(long buyerId) {
        return Db.list(SELECT + "WHERE buyer_id = ? ORDER BY created_at DESC, id DESC", MAPPER, buyerId);
    }

    public List<PurchaseOrder> findBySellerId(long sellerId) {
        return Db.list(SELECT + "WHERE seller_id = ? ORDER BY created_at DESC, id DESC", MAPPER, sellerId);
    }

    public List<PurchaseOrder> findByStatus(OrderStatus status) {
        return Db.list(SELECT + "WHERE status = ? ORDER BY updated_at ASC, id ASC", MAPPER, status);
    }

    /* ------------------------------------------------------------------
     * Czerwona kropka (nieprzeczytane zmiany)
     * ------------------------------------------------------------------ */

    public long countBuyerUnread(long buyerId) {
        return Db.one("SELECT COUNT(*) FROM orders WHERE buyer_id = ? AND buyer_unread = 1",
                rs -> rs.getLong(1), buyerId).orElse(0L);
    }

    public long countSellerUnread(long sellerId) {
        return Db.one("SELECT COUNT(*) FROM orders WHERE seller_id = ? AND seller_unread = 1",
                rs -> rs.getLong(1), sellerId).orElse(0L);
    }

    public int clearBuyerUnread(long userId) {
        return Db.update("UPDATE orders SET buyer_unread = 0 WHERE buyer_id = ? AND buyer_unread = 1", userId);
    }

    public int clearSellerUnread(long userId) {
        return Db.update("UPDATE orders SET seller_unread = 0 WHERE seller_id = ? AND seller_unread = 1", userId);
    }

    /* ------------------------------------------------------------------
     * Zapytania dla schedulera (tylko identyfikatory)
     * ------------------------------------------------------------------ */

    public List<Long> findIdsPendingExpired(OrderStatus status, LocalDateTime now) {
        return Db.list("SELECT id FROM orders WHERE status = ? AND checkout_expires_at < ?",
                ID_MAPPER, status, now);
    }

    public List<Long> findIdsShipDeadlinePassed(OrderStatus status, LocalDateTime now) {
        return Db.list("SELECT id FROM orders WHERE status = ? AND ship_deadline < ?",
                ID_MAPPER, status, now);
    }

    public List<Long> findIdsShippedBefore(OrderStatus status, LocalDateTime threshold) {
        return Db.list("SELECT id FROM orders WHERE status = ? AND shipped_at < ?",
                ID_MAPPER, status, threshold);
    }

    public List<Long> findIdsConfirmDeadlinePassed(OrderStatus status, LocalDateTime now) {
        return Db.list("SELECT id FROM orders WHERE status = ? AND confirm_deadline < ?",
                ID_MAPPER, status, now);
    }

    /* ------------------------------------------------------------------
     * Zapis
     * ------------------------------------------------------------------ */

    /** Dodaje zamowienie, ustawia createdAt/updatedAt oraz wygenerowane id. */
    public PurchaseOrder insert(PurchaseOrder order) {
        LocalDateTime now = LocalDateTime.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        String placeholders = "?" + ", ?".repeat(COLUMNS.length - 1);

        long id = Db.insert(
                "INSERT INTO orders (" + String.join(", ", COLUMNS) + ") VALUES (" + placeholders + ")",
                values(order)
        );

        order.setId(id);
        return order;
    }

    /** Zapisuje wszystkie pola zamowienia i odswieza updatedAt (jak @PreUpdate w Springu). */
    public void update(PurchaseOrder order) {
        order.setUpdatedAt(LocalDateTime.now());

        StringBuilder sql = new StringBuilder("UPDATE orders SET ");
        for (int i = 0; i < COLUMNS.length; i++) {
            sql.append(i == 0 ? "" : ", ").append(COLUMNS[i]).append(" = ?");
        }
        sql.append(" WHERE id = ?");

        Object[] values = values(order);
        Object[] params = java.util.Arrays.copyOf(values, values.length + 1);
        params[values.length] = order.getId();

        Db.update(sql.toString(), params);
    }

    /** Wartosci w kolejnosci kolumn z COLUMNS. */
    private static Object[] values(PurchaseOrder o) {
        return new Object[]{
                o.getOfferId(), o.getOfferTitle(), o.getBuyerId(), o.getSellerId(),
                o.getAmount(), o.getShippingCost(), o.getServiceFee(), o.getPaymentFee(), o.getTotalAmount(),
                o.getCurrency(), o.getStatus(),
                o.getStripeSessionId(), o.getStripeCheckoutUrl(), o.getStripePaymentIntentId(),
                o.getStripeChargeId(), o.getStripeTransferId(),
                o.getCheckoutExpiresAt(), o.getPaidAt(), o.getShipDeadline(), o.getShippedAt(),
                o.getDeliveredAt(), o.getConfirmDeadline(), o.getCompletedAt(),
                o.getDisputeReason(),
                o.getShippingCity(), o.getShippingStreet(), o.getShippingPostalCode(), o.getShippingPhone(),
                o.isBuyerUnread(), o.isSellerUnread(),
                o.getCreatedAt(), o.getUpdatedAt()
        };
    }
}
