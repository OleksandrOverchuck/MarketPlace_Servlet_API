package pl.jollycart.payment;

import jakarta.validation.constraints.NotNull;

/** Zadanie startu platnosci: oferta + adres dostawy podany w oknie "Dane do wysylki". */
public record CheckoutRequest(
        @NotNull(message = "Brak oferty")
        Long offerId,

        String city,
        String street,
        String postalCode,
        String phone
) {
}
