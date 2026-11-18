package pl.jollycart.payment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stripe.Stripe;

/*
 * Terminy z opisu procesu pilnuje scheduler (co minute), a nie sprzedawca
 * czy kupujacy - dzieki temu nikt nie "zawiesi" cudzych pieniedzy.
 * Odpowiednik PaymentScheduler (@Scheduled) ze Spring Boot.
 */
public final class PaymentScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentScheduler.class);

    private final OrderDao orderDao;
    private final OrderService orderService;
    private final long autoDeliverDays;

    private ScheduledExecutorService executor;

    public PaymentScheduler(OrderDao orderDao, OrderService orderService, PaymentConfig config) {
        this.orderDao = orderDao;
        this.orderService = orderService;
        this.autoDeliverDays = config.autoDeliverDays();
    }

    /** Pierwszy przebieg po 30 s, kolejne 60 s po zakonczeniu poprzedniego (jak fixedDelay w Springu). */
    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "payment-scheduler");
            thread.setDaemon(true);
            return thread;
        });

        executor.scheduleWithFixedDelay(this::run, 30, 60, TimeUnit.SECONDS);
    }

    public void stop() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    private void run() {

        // Wyjatek wyrzucony z zadania anulowalby wszystkie kolejne przebiegi - lapiemy wszystko.
        try {
            // Bez klucza Stripe (np. srodowisko bez platnosci) nic nie robimy.
            if (Stripe.apiKey == null || Stripe.apiKey.isBlank()) {
                return;
            }

            LocalDateTime now = LocalDateTime.now();

            process("wygasła płatność",
                    orderDao.findIdsPendingExpired(OrderStatus.PENDING_PAYMENT, now),
                    orderService::expirePending);

            process("brak wysyłki w terminie",
                    orderDao.findIdsShipDeadlinePassed(OrderStatus.PAID, now),
                    orderService::refundMissedShipment);

            process("auto-odbiór",
                    orderDao.findIdsShippedBefore(OrderStatus.SHIPPED, now.minusDays(autoDeliverDays)),
                    orderService::autoDeliver);

            process("auto-wypłata",
                    orderDao.findIdsConfirmDeadlinePassed(OrderStatus.DELIVERED, now),
                    orderService::autoRelease);

        } catch (Exception e) {
            log.error("Scheduler płatności: nieoczekiwany błąd", e);
        }
    }

    private void process(String label, List<Long> ids, Consumer<Long> action) {
        for (Long id : ids) {
            try {
                action.accept(id);
            } catch (Exception e) {
                // Jedno wadliwe zamowienie nie blokuje reszty;
                // zostanie ponowione w nastepnym przebiegu.
                log.error("Scheduler [{}]: zamówienie {} - błąd", label, id, e);
            }
        }
    }
}
