package pl.jollycart.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stripe.exception.StripeException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.Transfer;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.ChargeRetrieveParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.TransferCreateParams;
import com.stripe.param.checkout.SessionCreateParams;

import pl.jollycart.db.Db;
import pl.jollycart.notification.OrderPaidListener;
import pl.jollycart.offer.Offer;
import pl.jollycart.offer.OfferDao;
import pl.jollycart.offer.OfferStatus;
import pl.jollycart.offer.OfferType;
import pl.jollycart.payment.PriceCalculator.PriceBreakdown;
import pl.jollycart.user.AccountStatus;
import pl.jollycart.user.User;
import pl.jollycart.user.UserDao;
import pl.jollycart.web.StatusException;

/*
 * Caly cykl zycia platnosci (escrow) - port OrderService ze Spring Boot, budowany etapami:
 *
 *   PENDING_PAYMENT --(zaplacono)--> PAID --(sprzedawca wyslal)--> SHIPPED
 *        |                            |                               |
 *        | (wygaslo/anulowano)        | (48h bez wysylki)             | (kupujacy odebral)
 *        v                            v                               v
 *    CANCELLED                    REFUNDED                        DELIVERED
 *                                                                     |
 *                      (potwierdzenie lub 24h bez reakcji)            |  (zgloszenie problemu)
 *                                   v                                 v
 *                               COMPLETED  <---- admin: RELEASE ---- DISPUTED ---- admin: REFUND --> REFUNDED
 *
 * Pieniadze po zaplacie leza na koncie platformy w Stripe (nie u sprzedawcy).
 * Dopiero przejscie do COMPLETED wykonuje Transfer na konto sprzedawcy.
 */
public final class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    // Minimalna kwota platnosci karta w PLN w Stripe to 2,00 zl
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("2.00");

    private final OfferDao offerDao;
    private final OrderDao orderDao;
    private final UserDao userDao;
    private final SellerAccountService sellerAccountService;
    private final PriceCalculator priceCalculator;
    private final String currency;
    private final int checkoutMinutes;
    private final String frontendUrl;
    private final int shipHours;
    private final int confirmHours;
    private final SellerAccountDao sellerAccountDao;
    private final OrderPaidListener orderPaidListener;
    private final Executor asyncExecutor;

    public OrderService(
            OfferDao offerDao,
            OrderDao orderDao,
            UserDao userDao,
            SellerAccountService sellerAccountService,
            SellerAccountDao sellerAccountDao,
            PriceCalculator priceCalculator,
            PaymentConfig config,
            String frontendUrl,
            OrderPaidListener orderPaidListener,
            Executor asyncExecutor
    ) {
        this.offerDao = offerDao;
        this.orderDao = orderDao;
        this.userDao = userDao;
        this.sellerAccountService = sellerAccountService;
        this.priceCalculator = priceCalculator;
        this.currency = config.currency().toLowerCase();
        // Stripe wymaga, by sesja Checkout trwala co najmniej 30 minut
        this.checkoutMinutes = Math.max(30, config.checkoutMinutes());
        this.frontendUrl = frontendUrl;
        this.shipHours = config.shipHours();
        this.confirmHours = config.confirmHours();
        this.sellerAccountDao = sellerAccountDao;
        this.orderPaidListener = orderPaidListener;
        this.asyncExecutor = asyncExecutor;
    }

    // =====================================================================
    //  0. WYCENA: tabela kosztow pokazywana w oknie "Dane do wysylki"
    // =====================================================================

    /*
     * Tylko odczyt - niczego nie rezerwuje. Przegladarka niczego nie liczy,
     * a przy zakupie startCheckout i tak liczy sume od nowa.
     */
    public QuoteResponse quote(long offerId) {

        Offer offer = offerDao.findById(offerId)
                .orElseThrow(() -> new IllegalArgumentException("Oferta nie została znaleziona"));

        if (offer.type() != OfferType.SALE || !offer.onlinePaymentEnabled()) {
            throw new IllegalArgumentException("Ta oferta nie obsługuje płatności przez JollyCart");
        }

        if (offer.price() == null || offer.price().compareTo(MIN_AMOUNT) < 0) {
            throw new IllegalArgumentException("Minimalna kwota płatności to 2,00 zł");
        }

        PriceBreakdown b = priceCalculator.calculate(offer.price(), offer.shippingCost());

        return new QuoteResponse(
                offer.id(),
                currency,
                b.itemPrice(),
                b.shippingCost(),
                b.serviceFee(),
                b.paymentFee(),
                b.total()
        );
    }

    // =====================================================================
    //  1. START PLATNOSCI (kupujacy klika "Kup przez JollyCart")
    // =====================================================================

    /*
     * Wszystko w jednej transakcji: rezerwacja oferty (z blokada wiersza), zapis zamowienia
     * i utworzenie sesji Stripe. Gdy Stripe zawiedzie, transakcja sie cofa i oferta wraca
     * do sprzedazy. Zwraca zamowienie z adresem strony platnosci (checkoutUrl).
     */
    public OrderResponse startCheckout(
            User buyer,
            long offerId,
            String city,
            String street,
            String postalCode,
            String phone
    ) {
        StripeSetup.assertConfigured();

        ShippingData shipping = validateShipping(city, street, postalCode, phone);

        return Db.inTransaction(() -> createCheckout(buyer, offerId, shipping));
    }

    private OrderResponse createCheckout(User buyer, long offerId, ShippingData shipping) {

        // Blokada wiersza oferty - dwoch kupujacych nie zarezerwuje jej jednoczesnie
        Offer offer = offerDao.findByIdForUpdate(offerId)
                .orElseThrow(() -> new IllegalArgumentException("Oferta nie została znaleziona"));

        if (offer.type() != OfferType.SALE) {
            throw new IllegalArgumentException("Można kupić tylko ofertę typu sprzedaż");
        }
        if (!offer.onlinePaymentEnabled()) {
            throw new IllegalArgumentException("Ta oferta nie obsługuje płatności przez JollyCart");
        }
        if (offer.price() == null || offer.price().compareTo(MIN_AMOUNT) < 0) {
            throw new IllegalArgumentException("Minimalna kwota płatności to 2,00 zł");
        }

        if (offer.userId().equals(buyer.id())) {
            throw new IllegalArgumentException("Nie możesz kupić własnej oferty");
        }

        // Oferta niedostepna (juz zarezerwowana, sprzedana, ukryta) - 409
        if (offer.status() != OfferStatus.ACTIVE) {
            throw StatusException.conflict("Ta oferta nie jest już dostępna");
        }

        User seller = userDao.findById(offer.userId())
                .orElseThrow(() -> new IllegalArgumentException("Użytkownik nie został znaleziony"));

        if (seller.status() != AccountStatus.ACTIVE) {
            throw StatusException.conflict("Sprzedawca jest obecnie niedostępny");
        }

        if (!sellerAccountService.isPayoutReady(seller.id())) {
            throw StatusException.conflict("Sprzedawca nie skonfigurował jeszcze konta do wypłat");
        }

        PriceBreakdown price = priceCalculator.calculate(offer.price(), offer.shippingCost());

        // Rezerwacja oferty na czas platnosci
        offerDao.updateStatus(offer.id(), OfferStatus.RESERVED);

        PurchaseOrder order = new PurchaseOrder();
        order.setOfferId(offer.id());
        order.setOfferTitle(offer.title());
        order.setBuyerId(buyer.id());
        order.setSellerId(seller.id());
        order.setAmount(price.itemPrice());
        order.setShippingCost(price.shippingCost());
        order.setServiceFee(price.serviceFee());
        order.setPaymentFee(price.paymentFee());
        order.setTotalAmount(price.total());
        order.setCurrency(currency);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setBuyerUnread(false);
        order.setSellerUnread(false);
        order.setShippingCity(shipping.city());
        order.setShippingStreet(shipping.street());
        order.setShippingPostalCode(shipping.postalCode());
        order.setShippingPhone(shipping.phone());
        order.setCheckoutExpiresAt(LocalDateTime.now().plusMinutes(checkoutMinutes));

        // Zapis, zeby miec id zamowienia do metadanych Stripe
        orderDao.insert(order);

        try {
            String successUrl = frontendUrl + "/frontend/pages/orders.html?payment=success&orderId=" + order.getId();
            String cancelUrl = frontendUrl + "/frontend/pages/offer-details.html?id=" + offer.id()
                    + "&payment=cancelled&orderId=" + order.getId();

            SessionCreateParams.Builder params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .setCustomerEmail(buyer.email())
                    .setClientReferenceId(String.valueOf(order.getId()))
                    .putMetadata("orderId", String.valueOf(order.getId()))
                    // Sesja wygasa po checkoutMinutes - spojnie z checkoutExpiresAt w zamowieniu
                    .setExpiresAt(java.time.Instant.now().plusSeconds(checkoutMinutes * 60L).getEpochSecond())
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    // Laczy te platnosc z pozniejszym przelewem do sprzedawcy
                    .setPaymentIntentData(
                            SessionCreateParams.PaymentIntentData.builder()
                                    .setTransferGroup("ORDER_" + order.getId())
                                    .putMetadata("orderId", String.valueOf(order.getId()))
                                    .build()
                    )
                    .addLineItem(lineItem(offer.title(), price.itemPrice()))
                    .addAllLineItem(extraLineItems(order));

            // Klucz idempotencji chroni przed podwojna sesja przy ponowieniu zadania
            Session session = Session.create(
                    params.build(),
                    RequestOptions.builder().setIdempotencyKey("checkout-order-" + order.getId()).build()
            );

            order.setStripeSessionId(session.getId());
            order.setStripeCheckoutUrl(session.getUrl());
            orderDao.update(order);

        } catch (StripeException e) {
            // Wyjatek cofnie transakcje: oferta wroci do ACTIVE, zamowienie zniknie
            throw new PaymentGatewayException(
                    "Nie udało się utworzyć płatności: " + e.getMessage(), e);
        }

        return toResponse(order, buyer.id());
    }

    /* Kupujacy rezygnuje (np. wrocil ze strony platnosci przyciskiem "wstecz"). */
    public OrderResponse cancelPending(User buyer, long orderId) {

        return Db.inTransaction(() -> {
            PurchaseOrder order = lockOrder(orderId);

            requireBuyer(order, buyer);

            if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
                return toResponse(order, buyer.id());
            }

            settleOrCancelPending(order);
            orderDao.update(order);

            return toResponse(order, buyer.id());
        });
    }

    // =====================================================================
    //  2. WEBHOOKI STRIPE
    // =====================================================================

    /*
     * checkout.session.completed - kupujacy zaplacil.
     *
     * Nie ufamy samej tresci zdarzenia: pobieramy sesje ze Stripe po ID
     * i sprawdzamy payment_status. Metoda jest idempotentna (Stripe moze
     * wyslac to samo zdarzenie wielokrotnie).
     */
    public void handleCheckoutCompleted(String sessionId) {

        Db.inTransaction(() -> {
            PurchaseOrder order = orderDao.findBySessionIdForUpdate(sessionId).orElse(null);

            if (order == null) {
                log.warn("Webhook: brak zamówienia dla sesji {}", sessionId);
                return null;
            }

            try {
                Session session = Session.retrieve(sessionId);

                if (!"paid".equals(session.getPaymentStatus())) {
                    return null;
                }

                if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    applyPaid(order, session);
                    orderDao.update(order);
                    log.info("Zamówienie {} opłacone", order.getId());

                } else if (order.getStatus() == OrderStatus.CANCELLED) {
                    // Zaplacono po tym, jak zamowienie zostalo anulowane
                    // (wyscig z wygasnieciem) - oddajemy pieniadze.
                    order.setStripePaymentIntentId(session.getPaymentIntent());
                    refundPayment(order);
                    order.setStatus(OrderStatus.REFUNDED);
                    orderDao.update(order);
                    log.warn("Zamówienie {} zapłacone po anulowaniu - zwrot", order.getId());
                }
                // pozostale statusy: zdarzenie juz obsluzone

            } catch (StripeException e) {
                // Rzucamy dalej -> webhook zwroci 500 -> Stripe ponowi wysylke.
                throw new PaymentGatewayException("Błąd przetwarzania webhooka", e);
            }

            return null;
        });
    }

    /* checkout.session.expired - kupujacy nie zaplacil w czasie sesji. */
    public void handleCheckoutExpired(String sessionId) {

        Db.inTransaction(() -> {
            PurchaseOrder order = orderDao.findBySessionIdForUpdate(sessionId).orElse(null);

            if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
                return null;
            }

            order.setStatus(OrderStatus.CANCELLED);
            orderDao.update(order);
            releaseOffer(order.getOfferId(), OfferStatus.ACTIVE);
            log.info("Zamówienie {} wygasło - oferta ponownie dostępna", order.getId());

            return null;
        });
    }

    /* Sprawdza w Stripe, czy zamowienie zostalo oplacone (gdy webhook jeszcze nie dotarl). */
    public OrderResponse syncPending(User buyer, long orderId) {

        return Db.inTransaction(() -> {
            PurchaseOrder order = lockOrder(orderId);

            requireBuyer(order, buyer);

            if (order.getStatus() == OrderStatus.PENDING_PAYMENT
                    && order.getStripeSessionId() != null) {
                try {
                    Session session = Session.retrieve(order.getStripeSessionId());

                    if ("complete".equals(session.getStatus())
                            && "paid".equals(session.getPaymentStatus())) {
                        applyPaid(order, session);
                        orderDao.update(order);
                    }
                } catch (StripeException e) {
                    throw new PaymentGatewayException("Nie udało się sprawdzić płatności.", e);
                }
            }

            return toResponse(order, buyer.id());
        });
    }

    // =====================================================================
    //  3. WYSYLKA, ODBIOR, POTWIERDZENIE
    // =====================================================================

    public OrderResponse markShipped(User seller, long orderId) {

        return Db.inTransaction(() -> {
            PurchaseOrder order = lockOrder(orderId);

            if (!order.getSellerId().equals(seller.id())) {
                throw new IllegalArgumentException("To nie jest Twoja sprzedaż");
            }

            requireStatus(order, OrderStatus.PAID,
                    "Wysyłkę można oznaczyć tylko dla opłaconego zamówienia");

            order.setStatus(OrderStatus.SHIPPED);
            order.setShippedAt(LocalDateTime.now());

            // Kupujacy dowiaduje sie o wysylce -> czerwona kropka
            order.setBuyerUnread(true);

            orderDao.update(order);

            return toResponse(order, seller.id());
        });
    }

    /* Kupujacy odebral towar - od teraz ma confirmHours na decyzje. */
    public OrderResponse markReceived(User buyer, long orderId) {

        return Db.inTransaction(() -> {
            PurchaseOrder order = lockOrder(orderId);

            requireBuyer(order, buyer);
            requireStatus(order, OrderStatus.SHIPPED,
                    "Odbiór można potwierdzić po tym, jak sprzedawca wyśle towar");

            markDelivered(order);
            orderDao.update(order);

            return toResponse(order, buyer.id());
        });
    }

    /* Kupujacy: "towar jest OK" -> wyplata dla sprzedawcy. */
    public OrderResponse confirmOrder(User buyer, long orderId) {

        return Db.inTransaction(() -> {
            PurchaseOrder order = lockOrder(orderId);

            requireBuyer(order, buyer);

            if (order.getStatus() != OrderStatus.SHIPPED
                    && order.getStatus() != OrderStatus.DELIVERED) {
                throw StatusException.conflict("Zamówienia nie można teraz potwierdzić");
            }

            releaseFunds(order);
            orderDao.update(order);

            return toResponse(order, buyer.id());
        });
    }

    // =====================================================================
    //  5. LISTY I CZERWONA KROPKA
    // =====================================================================

    public List<OrderResponse> getPurchases(User user) {
        return orderDao.findByBuyerId(user.id())
                .stream()
                .map(o -> toResponse(o, user.id()))
                .toList();
    }

    public List<OrderResponse> getSales(User user) {
        return orderDao.findBySellerId(user.id())
                .stream()
                // Sprzedawca nie musi widziec nieoplaconych prob zakupu
                .filter(o -> o.getStatus() != OrderStatus.PENDING_PAYMENT
                        && o.getStatus() != OrderStatus.CANCELLED)
                .map(o -> toResponse(o, user.id()))
                .toList();
    }

    public record UnreadOrders(long purchases, long sales) {
    }

    public UnreadOrders countUnread(User user) {
        return new UnreadOrders(
                orderDao.countBuyerUnread(user.id()),
                orderDao.countSellerUnread(user.id())
        );
    }

    /* Uzytkownik wszedl w "Zamowienia" - gasimy czerwona kropke. */
    public void markSeen(User user) {
        Db.inTransaction(() -> {
            orderDao.clearBuyerUnread(user.id());
            orderDao.clearSellerUnread(user.id());
            return null;
        });
    }

    // =====================================================================
    //  WNETRZE
    // =====================================================================

    private void applyPaid(PurchaseOrder order, Session session) throws StripeException {

        String paymentIntentId = session.getPaymentIntent();
        PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

        LocalDateTime now = LocalDateTime.now();

        order.setStripePaymentIntentId(paymentIntentId);
        order.setStripeChargeId(paymentIntent.getLatestCharge());
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(now);
        order.setShipDeadline(now.plusHours(shipHours));

        // Sprzedawca ma nowa sprzedaz do obsluzenia -> czerwona kropka
        order.setSellerUnread(true);

        // Mail dla sprzedawcy po zatwierdzeniu transakcji. Metoda jest wywolywana tylko
        // przy przejsciu PENDING_PAYMENT -> PAID, wiec sprzedawca dostaje dokladnie jeden mail na zamowienie.
        OrderPaidEvent event = new OrderPaidEvent(
                order.getId(),
                order.getSellerId(),
                order.getBuyerId(),
                order.getOfferTitle(),
                order.getSellerPayout(),
                order.getCurrency(),
                order.getShipDeadline(),
                shipHours
        );

        Db.afterCommit(() -> asyncExecutor.execute(() -> orderPaidListener.onOrderPaid(event)));
    }

    /*
     * Dla zamowienia PENDING_PAYMENT: jesli Stripe mowi, ze jednak zaplacono,
     * zapisujemy platnosc; w przeciwnym razie zamykamy sesje i zwalniamy oferte.
     * Wywolujacy zapisuje zamowienie (orderDao.update).
     */
    private void settleOrCancelPending(PurchaseOrder order) {

        try {
            if (order.getStripeSessionId() != null) {
                Session session = Session.retrieve(order.getStripeSessionId());

                if ("complete".equals(session.getStatus())
                        && "paid".equals(session.getPaymentStatus())) {
                    applyPaid(order, session);
                    return;
                }

                if ("open".equals(session.getStatus())) {
                    session.expire();
                }
            }
        } catch (StripeException e) {
            throw new PaymentGatewayException("Nie udało się zamknąć sesji płatności.", e);
        }

        order.setStatus(OrderStatus.CANCELLED);
        releaseOffer(order.getOfferId(), OfferStatus.ACTIVE);
    }

    private void refundPayment(PurchaseOrder order) {

        if (order.getStripePaymentIntentId() == null) {
            throw StatusException.conflict("Brak płatności do zwrotu");
        }

        try {
            Refund.create(
                    RefundCreateParams.builder()
                            .setPaymentIntent(order.getStripePaymentIntentId())
                            .putMetadata("orderId", String.valueOf(order.getId()))
                            .build(),
                    RequestOptions.builder()
                            .setIdempotencyKey("refund-order-" + order.getId())
                            .build()
            );
        } catch (StripeException e) {
            throw new PaymentGatewayException("Nie udało się zwrócić płatności.", e);
        }
    }

    /* Zmienia status oferty tylko, jesli jest nadal zarezerwowana przez to zamowienie. */
    private void releaseOffer(Long offerId, OfferStatus newStatus) {
        offerDao.findById(offerId).ifPresent(offer -> {
            if (offer.status() == OfferStatus.RESERVED) {
                offerDao.updateStatus(offer.id(), newStatus);
            }
        });
    }

    private void markDelivered(PurchaseOrder order) {
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(now);
        order.setConfirmDeadline(now.plusHours(confirmHours));
    }

    /*
     * WYPLATA: przelew ze srodkow platformy na konto Stripe sprzedawcy.
     *
     * source_transaction wiaze Transfer z konkretna platnoscia kupujacego,
     * a klucz idempotencji gwarantuje, ze przy ponowieniu (np. po bledzie
     * zapisu do bazy) pieniadze nie zostana wyplacone dwa razy.
     * Wywolujacy zapisuje zamowienie (orderDao.update).
     */
    private void releaseFunds(PurchaseOrder order) {

        if (order.getStripeTransferId() != null) {
            order.setStatus(OrderStatus.COMPLETED);
            return;
        }

        SellerAccount sellerAccount = sellerAccountDao.findByUserId(order.getSellerId())
                .orElseThrow(() -> StatusException.conflict("Sprzedawca nie ma konta wypłat"));

        // Przelew MUSI byc powiazany z platnoscia kupujacego (source_transaction).
        String chargeId = order.getStripeChargeId();

        try {
            if ((chargeId == null || chargeId.isBlank())
                    && order.getStripePaymentIntentId() != null) {
                chargeId = PaymentIntent
                        .retrieve(order.getStripePaymentIntentId())
                        .getLatestCharge();
                order.setStripeChargeId(chargeId);
            }

            if (chargeId == null || chargeId.isBlank()) {
                throw StatusException.conflict("Brak powiązanej płatności - nie można wypłacić środków");
            }

            // Sprzedawca dostaje cene towaru + dostawe; oplata serwisowa zostaje
            // u JollyCart. Stripe pobiera prowizje od platnosci kupujacego, wiec
            // przekazujemy najwyzej kwote netto (po prowizji), inaczej przelew
            // przekracza dostepne srodki (balance_insufficient).
            long transferAmount = toMinorUnits(order.getSellerPayout());

            Charge charge = Charge.retrieve(
                    chargeId,
                    ChargeRetrieveParams.builder()
                            .addExpand("balance_transaction")
                            .build(),
                    null
            );

            BalanceTransaction balanceTransaction = charge.getBalanceTransactionObject();

            if (balanceTransaction != null && balanceTransaction.getNet() != null) {
                transferAmount = Math.min(transferAmount, balanceTransaction.getNet());
            }

            Transfer transfer = Transfer.create(
                    TransferCreateParams.builder()
                            .setAmount(transferAmount)
                            .setCurrency(order.getCurrency())
                            .setDestination(sellerAccount.stripeAccountId())
                            .setSourceTransaction(chargeId)
                            .setTransferGroup("ORDER_" + order.getId())
                            .putMetadata("orderId", String.valueOf(order.getId()))
                            .build(),
                    RequestOptions.builder()
                            // Kwota w kluczu: bezpieczne ponowienie tej samej wyplaty,
                            // a poprawiona kwota nie koliduje z wczesniejsza nieudana proba.
                            .setIdempotencyKey("transfer-order-" + order.getId() + "-" + transferAmount)
                            .build()
            );

            order.setStripeTransferId(transfer.getId());

        } catch (StripeException e) {
            throw new PaymentGatewayException(
                    "Nie udało się wypłacić środków sprzedawcy: " + e.getMessage(), e);
        }

        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        releaseOffer(order.getOfferId(), OfferStatus.SOLD);
    }

    private void requireStatus(PurchaseOrder order, OrderStatus expected, String message) {
        if (order.getStatus() != expected) {
            throw StatusException.conflict(message);
        }
    }

    private PurchaseOrder lockOrder(long orderId) {
        return orderDao.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Zamówienie nie zostało znalezione"));
    }

    private void requireBuyer(PurchaseOrder order, User user) {
        if (!order.getBuyerId().equals(user.id())) {
            throw new IllegalArgumentException("To nie jest Twoje zamówienie");
        }
    }

    private record ShippingData(
            String city,
            String street,
            String postalCode,
            String phone
    ) {
    }

    /* Sprawdza i normalizuje adres dostawy podany przed platnoscia. */
    private ShippingData validateShipping(
            String city,
            String street,
            String postalCode,
            String phone
    ) {
        String c = city == null ? "" : city.trim();
        String s = street == null ? "" : street.trim();

        if (c.isEmpty()) {
            throw new IllegalArgumentException("Podaj miasto");
        }
        if (c.length() > 100) {
            throw new IllegalArgumentException("Nazwa miasta jest za długa");
        }
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Podaj ulicę i numer domu");
        }
        if (s.length() > 150) {
            throw new IllegalArgumentException("Nazwa ulicy jest za długa");
        }

        String postal = postalCode == null ? "" : postalCode.replaceAll("\\s", "");
        if (postal.matches("\\d{5}")) {
            postal = postal.substring(0, 2) + "-" + postal.substring(2);
        }
        if (!postal.matches("\\d{2}-\\d{3}")) {
            throw new IllegalArgumentException("Podaj kod pocztowy w formacie 00-000");
        }

        String ph = phone == null ? "" : phone.replaceAll("[\\s\\-()]", "");
        if (!ph.matches("\\+?\\d{9,15}")) {
            throw new IllegalArgumentException("Podaj poprawny numer telefonu (np. 600 123 456)");
        }

        return new ShippingData(c, s, postal, ph);
    }

    private SessionCreateParams.LineItem lineItem(String name, BigDecimal amount) {
        return SessionCreateParams.LineItem.builder()
                .setQuantity(1L)
                .setPriceData(
                        SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currency)
                                .setUnitAmount(toMinorUnits(amount))
                                .setProductData(
                                        SessionCreateParams.LineItem.PriceData
                                                .ProductData.builder()
                                                .setName(name)
                                                .build()
                                )
                                .build()
                )
                .build();
    }

    /* Dostawa (tylko gdy > 0) oraz obie oplaty. Stripe nie przyjmuje pozycji za 0 zl. */
    private List<SessionCreateParams.LineItem> extraLineItems(PurchaseOrder order) {

        List<SessionCreateParams.LineItem> items = new ArrayList<>();

        if (order.getShippingCost().signum() > 0) {
            items.add(lineItem("Dostawa", order.getShippingCost()));
        }
        if (order.getServiceFee().signum() > 0) {
            items.add(lineItem("Opłata serwisowa JollyCart (ochrona kupującego)", order.getServiceFee()));
        }
        if (order.getPaymentFee().signum() > 0) {
            items.add(lineItem("Opłata operatora płatności (Stripe, szacunkowa)", order.getPaymentFee()));
        }

        return items;
    }

    // Stripe liczy w groszach: 12,34 zl -> 1234
    private long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();
    }

    private OrderResponse toResponse(PurchaseOrder o, Long viewerId) {

        String buyerNick = userDao.findById(o.getBuyerId())
                .map(User::nickname).orElse("(usunięty użytkownik)");
        String sellerNick = userDao.findById(o.getSellerId())
                .map(User::nickname).orElse("(usunięty użytkownik)");

        boolean viewerIsBuyer = viewerId != null && viewerId.equals(o.getBuyerId());
        boolean viewerIsSeller = viewerId != null && viewerId.equals(o.getSellerId());

        // Sprzedawca widzi adres dopiero, gdy zamowienie jest oplacone
        // i nie zostalo zwrocone / anulowane.
        boolean showAddress = viewerIsBuyer
                || (viewerIsSeller && switch (o.getStatus()) {
                    case PAID, SHIPPED, DELIVERED, DISPUTED, COMPLETED -> true;
                    default -> false;
                });

        return new OrderResponse(
                o.getId(),
                o.getOfferId(),
                o.getOfferTitle(),
                o.getAmount(),
                o.getShippingCost(),
                o.getServiceFee(),
                o.getPaymentFee(),
                o.getTotalAmount(),
                o.getSellerPayout(),
                o.getCurrency(),
                o.getStatus(),
                o.getBuyerId(),
                buyerNick,
                o.getSellerId(),
                sellerNick,
                o.getCreatedAt(),
                o.getPaidAt(),
                o.getShipDeadline(),
                o.getShippedAt(),
                o.getDeliveredAt(),
                o.getConfirmDeadline(),
                o.getCompletedAt(),
                o.getDisputeReason(),
                viewerIsBuyer && o.getStatus() == OrderStatus.PENDING_PAYMENT
                        ? o.getStripeCheckoutUrl()
                        : null,
                showAddress ? o.getShippingCity() : null,
                showAddress ? o.getShippingStreet() : null,
                showAddress ? o.getShippingPostalCode() : null,
                showAddress ? o.getShippingPhone() : null
        );
    }
}
