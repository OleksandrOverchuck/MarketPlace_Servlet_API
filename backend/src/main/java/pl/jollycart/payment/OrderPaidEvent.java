package pl.jollycart.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/*
 * Zdarzenie: kupujacy oplacil zamowienie.
 * Same typy proste - powiadomienie i mail powstaja w osobnym watku,
 * PO zatwierdzeniu transakcji platnosci (blad maila nigdy nie cofnie zapisu platnosci).
 */
public record OrderPaidEvent(
        Long orderId,
        Long sellerId,
        Long buyerId,
        String offerTitle,
        BigDecimal amount,
        String currency,
        LocalDateTime shipDeadline,
        long shipHours
) {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /* "2 dni" / "1 dzien" / "36 godz." - ile sprzedawca ma czasu na wysylke */
    public String shipWindowText() {
        if (shipHours > 0 && shipHours % 24 == 0) {
            long days = shipHours / 24;
            return days + (days == 1 ? " dzień" : " dni");
        }
        return shipHours + " godz.";
    }

    public String shipDeadlineText() {
        return shipDeadline != null ? shipDeadline.format(DATE_FORMAT) : "-";
    }

    public String amountText() {
        String unit = "pln".equalsIgnoreCase(currency) ? "zł" : currency.toUpperCase();
        return amount.setScale(2).toPlainString().replace('.', ',') + " " + unit;
    }
}
