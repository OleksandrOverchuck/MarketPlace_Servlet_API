package pl.jollycart.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.jollycart.auth.EmailService;
import pl.jollycart.payment.OrderPaidEvent;
import pl.jollycart.user.User;
import pl.jollycart.user.UserDao;

/**
 * Wysyla sprzedawcy maila o oplaconym zamowieniu. Wywolywany dopiero PO zatwierdzeniu
 * transakcji i w osobnym watku (zob. OrderService), wiec blad SMTP jest tylko logowany
 * i nigdy nie cofa zapisu platnosci.
 */
public final class OrderPaidListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidListener.class);

    private final UserDao userDao;
    private final EmailService emailService;
    private final String frontendUrl;

    public OrderPaidListener(UserDao userDao, EmailService emailService, String frontendUrl) {
        this.userDao = userDao;
        this.emailService = emailService;
        this.frontendUrl = frontendUrl;
    }

    public void onOrderPaid(OrderPaidEvent event) {

        User seller = userDao.findById(event.sellerId()).orElse(null);
        User buyer = userDao.findById(event.buyerId()).orElse(null);

        if (seller == null || buyer == null) {
            log.warn("Zamówienie {}: brak sprzedawcy lub kupującego - pomijam mail", event.orderId());
            return;
        }

        try {
            emailService.sendOrderPaidNotification(
                    seller.email(),
                    seller.nickname(),
                    buyer.nickname(),
                    event.offerTitle(),
                    event.amountText(),
                    event.shipWindowText(),
                    event.shipDeadlineText(),
                    frontendUrl + "/frontend/pages/orders.html?tab=sales"
            );
        } catch (RuntimeException e) {
            log.warn("Zamówienie {}: nie udało się wysłać maila do sprzedawcy", event.orderId(), e);
        }
    }
}
