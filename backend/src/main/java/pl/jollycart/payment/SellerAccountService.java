package pl.jollycart.payment;

import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.AccountSession;
import com.stripe.model.LoginLink;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import com.stripe.param.AccountSessionCreateParams;

import pl.jollycart.db.DbException;
import pl.jollycart.user.User;
import pl.jollycart.web.StatusException;

/** Konta sprzedawcow w Stripe Connect (port SellerAccountService ze Spring Boot). */
public final class SellerAccountService {

    private final SellerAccountDao sellerAccountDao;
    private final String frontendUrl;
    private final String publishableKey;

    public SellerAccountService(SellerAccountDao sellerAccountDao, String frontendUrl, String publishableKey) {
        this.sellerAccountDao = sellerAccountDao;
        this.frontendUrl = frontendUrl;
        this.publishableKey = publishableKey;
    }

    // Zwraca adres formularza Stripe, w ktorym uzytkownik poda dane i konto bankowe.
    public String createOnboardingLink(User user) {

        StripeSetup.assertConfigured();

        try {
            SellerAccount seller = getOrCreateSeller(user);

            String settingsUrl = frontendUrl + "/frontend/pages/settings.html";

            AccountLink link = AccountLink.create(
                    AccountLinkCreateParams.builder()
                            .setAccount(seller.stripeAccountId())
                            .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                            .setRefreshUrl(settingsUrl + "?payouts=refresh")
                            .setReturnUrl(settingsUrl + "?payouts=return")
                            .build()
            );

            return link.getUrl();

        } catch (StripeException e) {
            throw new PaymentGatewayException(
                    "Nie udało się połączyć z operatorem płatności: " + e.getMessage(), e);
        }
    }

    // Jedno konto Stripe (Express) na uzytkownika - wspoldzielone przez oba warianty
    // (przekierowanie na Stripe oraz formularz osadzony w aplikacji).
    private SellerAccount getOrCreateSeller(User user) throws StripeException {

        SellerAccount existing = sellerAccountDao.findByUserId(user.id()).orElse(null);

        if (existing != null) {
            return existing;
        }

        Account account = Account.create(
                AccountCreateParams.builder()
                        .setType(AccountCreateParams.Type.EXPRESS)
                        .setCountry("PL")
                        .setEmail(user.email())
                        .setCapabilities(
                                AccountCreateParams.Capabilities.builder()
                                        .setTransfers(
                                                AccountCreateParams.Capabilities.Transfers
                                                        .builder()
                                                        .setRequested(true)
                                                        .build()
                                        )
                                        .build()
                        )
                        .putMetadata("jollycartUserId", String.valueOf(user.id()))
                        .build()
        );

        try {
            return sellerAccountDao.insert(user.id(), account.getId());
        } catch (DbException e) {
            // Rownolegle zadanie (np. podwojne klikniecie) zdazylo dodac konto - bierzemy jego wpis
            return sellerAccountDao.findByUserId(user.id()).orElseThrow(() -> e);
        }
    }

    // Wariant "w aplikacji": sesja dla komponentow Stripe osadzonych na stronie.
    // mode = "onboarding" (dodanie konta) lub "manage" (zmiana danych / konta bankowego).
    public EmbeddedSessionResponse createEmbeddedSession(User user, String mode) {

        StripeSetup.assertConfigured();

        if (publishableKey == null || publishableKey.isBlank()) {
            throw new PaymentGatewayException("Brak klucza publicznego Stripe (STRIPE_PUBLISHABLE_KEY).");
        }

        try {
            SellerAccount seller = getOrCreateSeller(user);

            AccountSessionCreateParams.Components.Builder components =
                    AccountSessionCreateParams.Components.builder();

            if ("manage".equals(mode)) {
                components.setAccountManagement(
                        AccountSessionCreateParams.Components.AccountManagement.builder()
                                .setEnabled(true)
                                .setFeatures(
                                        AccountSessionCreateParams.Components.AccountManagement
                                                .Features.builder()
                                                .setExternalAccountCollection(true)
                                                .build())
                                .build());
            } else {
                components.setAccountOnboarding(
                        AccountSessionCreateParams.Components.AccountOnboarding.builder()
                                .setEnabled(true)
                                .build());
            }

            AccountSession session = AccountSession.create(
                    AccountSessionCreateParams.builder()
                            .setAccount(seller.stripeAccountId())
                            .setComponents(components.build())
                            .build()
            );

            return new EmbeddedSessionResponse(session.getClientSecret(), publishableKey);

        } catch (StripeException e) {
            throw new PaymentGatewayException(
                    "Nie udało się otworzyć formularza konta: " + e.getMessage(), e);
        }
    }

    // Link do panelu Stripe, w ktorym sprzedawca zmienia konto bankowe do wyplat.
    public String createDashboardLink(User user) {

        StripeSetup.assertConfigured();

        SellerAccount seller = sellerAccountDao.findByUserId(user.id())
                .orElseThrow(() -> StatusException.conflict("Najpierw dodaj konto bankowe"));

        try {
            LoginLink link = LoginLink.createOnAccount(
                    seller.stripeAccountId(),
                    new java.util.HashMap<String, Object>()
            );

            return link.getUrl();

        } catch (StripeException e) {
            throw new PaymentGatewayException(
                    "Nie udało się otworzyć panelu konta: " + e.getMessage(), e);
        }
    }

    public SellerStatusResponse getStatus(User user) {

        SellerAccount seller = sellerAccountDao.findByUserId(user.id()).orElse(null);

        if (seller == null) {
            return new SellerStatusResponse(false, false, false);
        }

        StripeSetup.assertConfigured();

        try {
            Account account = Account.retrieve(seller.stripeAccountId());
            return new SellerStatusResponse(
                    true,
                    isTransfersActive(account),
                    Boolean.TRUE.equals(account.getDetailsSubmitted())
            );
        } catch (StripeException e) {
            throw new PaymentGatewayException("Nie udało się sprawdzić statusu konta wypłat.", e);
        }
    }

    // Czy sprzedawca moze dostac wyplate (uzywane przy dodawaniu oferty z platnoscia online i przy kupnie).
    // Pasuje do LongPredicate, ktorego oczekuje OfferService.
    public boolean isPayoutReady(long userId) {

        SellerAccount seller = sellerAccountDao.findByUserId(userId).orElse(null);

        if (seller == null) {
            return false;
        }

        StripeSetup.assertConfigured();

        try {
            return isTransfersActive(Account.retrieve(seller.stripeAccountId()));
        } catch (StripeException e) {
            throw new PaymentGatewayException("Nie udało się sprawdzić konta sprzedawcy.", e);
        }
    }

    private boolean isTransfersActive(Account account) {
        return account.getCapabilities() != null
                && "active".equals(account.getCapabilities().getTransfers());
    }
}
