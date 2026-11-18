package pl.jollycart.payment;

/** Status zamowienia (te same wartosci co w wersji Spring Boot - front opiera sie na nich). */
public enum OrderStatus {

    // Kupujacy jest na stronie platnosci Stripe, oferta zarezerwowana
    PENDING_PAYMENT,

    // Zaplacone, srodki trzymane przez Stripe; sprzedawca ma czas na wysylke
    PAID,

    // Sprzedawca oznaczyl wysylke
    SHIPPED,

    // Kupujacy odebral towar; biegnie czas na potwierdzenie / zgloszenie
    DELIVERED,

    // Kupujacy potwierdzil (lub minal czas) - srodki wyplacone sprzedawcy
    COMPLETED,

    // Kupujacy zglosil problem - srodki wstrzymane do decyzji administratora
    DISPUTED,

    // Platnosc nie doszla do skutku (anulowana / wygasla)
    CANCELLED,

    // Pieniadze wrocily do kupujacego
    REFUNDED
}
