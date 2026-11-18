package pl.jollycart.dispute;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import pl.jollycart.db.Db;
import pl.jollycart.db.RowMapper;

/** Zapytania SQL do tabeli order_disputes (odpowiednik OrderDisputeRepository). */
public final class OrderDisputeDao {

    private static final String COLUMNS =
            "id, order_id, reporter_id, title, description, status, resolution, resolver_id, "
                    + "resolver_nickname, resolver_note, created_at, resolved_at";

    private static final String SELECT = "SELECT " + COLUMNS + " FROM order_disputes ";

    private static final RowMapper<OrderDispute> MAPPER = rs -> {
        OrderDispute d = new OrderDispute();
        d.setId(rs.getLong("id"));
        d.setOrderId(rs.getLong("order_id"));
        d.setReporterId(rs.getLong("reporter_id"));
        d.setTitle(rs.getString("title"));
        d.setDescription(rs.getString("description"));
        d.setStatus(DisputeStatus.valueOf(rs.getString("status")));
        String resolution = rs.getString("resolution");
        d.setResolution(resolution == null ? null : DisputeResolution.valueOf(resolution));
        long resolverId = rs.getLong("resolver_id");
        d.setResolverId(rs.wasNull() ? null : resolverId);
        d.setResolverNickname(rs.getString("resolver_nickname"));
        d.setResolverNote(rs.getString("resolver_note"));
        d.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        d.setResolvedAt(rs.getObject("resolved_at", LocalDateTime.class));
        return d;
    };

    public Optional<OrderDispute> findById(long id) {
        return Db.one(SELECT + "WHERE id = ?", MAPPER, id);
    }

    /** Blokada wiersza do konca transakcji - dwoch moderatorow nie rozstrzygnie tego samego zgloszenia. */
    public Optional<OrderDispute> findByIdForUpdate(long id) {
        return Db.one(SELECT + "WHERE id = ? FOR UPDATE", MAPPER, id);
    }

    public boolean existsByOrderId(long orderId) {
        return Db.exists("SELECT 1 FROM order_disputes WHERE order_id = ?", orderId);
    }

    public long countByStatus(DisputeStatus status) {
        return Db.one("SELECT COUNT(*) FROM order_disputes WHERE status = ?",
                rs -> rs.getLong(1), status).orElse(0L);
    }

    /** "Aktualne": od najstarszych. */
    public List<OrderDispute> findByStatusOrderByCreatedAtAsc(DisputeStatus status) {
        return Db.list(SELECT + "WHERE status = ? ORDER BY created_at ASC, id ASC", MAPPER, status);
    }

    /** "Rozwiazane": 100 ostatnio rozstrzygnietych. */
    public List<OrderDispute> findTop100ResolvedByResolvedAtDesc() {
        return Db.list(SELECT + "WHERE status = ? ORDER BY resolved_at DESC, id DESC LIMIT 100",
                MAPPER, DisputeStatus.RESOLVED);
    }

    public OrderDispute insert(OrderDispute d) {
        if (d.getCreatedAt() == null) {
            d.setCreatedAt(LocalDateTime.now());
        }

        long id = Db.insert(
                "INSERT INTO order_disputes (order_id, reporter_id, title, description, status, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                d.getOrderId(), d.getReporterId(), d.getTitle(), d.getDescription(), d.getStatus(), d.getCreatedAt()
        );

        d.setId(id);
        return d;
    }

    /** Zapisuje rozstrzygniecie (status, decyzja, kto, uzasadnienie, kiedy). */
    public void updateResolution(OrderDispute d) {
        Db.update(
                "UPDATE order_disputes SET status = ?, resolution = ?, resolver_id = ?, "
                        + "resolver_nickname = ?, resolver_note = ?, resolved_at = ? WHERE id = ?",
                d.getStatus(), d.getResolution(), d.getResolverId(),
                d.getResolverNickname(), d.getResolverNote(), d.getResolvedAt(), d.getId()
        );
    }
}
