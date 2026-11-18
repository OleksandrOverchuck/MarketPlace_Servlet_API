package pl.jollycart.payment;

public record SellerStatusResponse(
        boolean accountCreated,     // uzytkownik zaczal konfiguracje
        boolean payoutsReady,       // Stripe potwierdzil dane, mozna wyplacac
        boolean detailsSubmitted    // formularz Stripe wypelniony do konca
) {
}
