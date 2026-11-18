package pl.jollycart.payment;

import java.time.LocalDateTime;
import java.util.Optional;

import pl.jollycart.db.Db;
import pl.jollycart.db.RowMapper;

/** Zapytania SQL do tabeli seller_accounts (odpowiednik SellerAccountRepository). */
public final class SellerAccountDao {

    private static final String COLUMNS = "id, user_id, stripe_account_id, created_at";

    private static final RowMapper<SellerAccount> MAPPER = rs -> new SellerAccount(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("stripe_account_id"),
            rs.getObject("created_at", LocalDateTime.class)
    );

    public Optional<SellerAccount> findByUserId(long userId) {
        return Db.one("SELECT " + COLUMNS + " FROM seller_accounts WHERE user_id = ?", MAPPER, userId);
    }

    public SellerAccount insert(long userId, String stripeAccountId) {
        LocalDateTime now = LocalDateTime.now();

        long id = Db.insert(
                "INSERT INTO seller_accounts (user_id, stripe_account_id, created_at) VALUES (?, ?, ?)",
                userId, stripeAccountId, now
        );

        return new SellerAccount(id, userId, stripeAccountId, now);
    }
}
