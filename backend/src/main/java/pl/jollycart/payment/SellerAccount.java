package pl.jollycart.payment;

import java.time.LocalDateTime;

/** Wiersz tabeli seller_accounts: konto Stripe Connect (Express) przypisane do uzytkownika. */
public record SellerAccount(
        Long id,
        Long userId,
        String stripeAccountId,
        LocalDateTime createdAt
) {
}
