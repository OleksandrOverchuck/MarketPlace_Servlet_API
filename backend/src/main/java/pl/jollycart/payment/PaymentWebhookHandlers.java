package pl.jollycart.payment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;

import pl.jollycart.web.Ctx;
import pl.jollycart.web.Router;

/**
 * Webhook Stripe (odpowiednik PaymentWebhookController). Adres jest publiczny (bez logowania) -
 * bezpieczenstwo zapewnia weryfikacja podpisu naglowka Stripe-Signature na SUROWYM body.
 */
public final class PaymentWebhookHandlers {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookHandlers.class);

    private final OrderService orderService;
    private final String webhookSecret;

    public PaymentWebhookHandlers(OrderService orderService, String webhookSecret) {
        this.orderService = orderService;
        this.webhookSecret = webhookSecret;
    }

    public void routes(Router router) {
        router.post("/api/payments/webhook", this::handleWebhook);
    }

    private void handleWebhook(Ctx ctx) throws IOException {

        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Brak STRIPE_WEBHOOK_SECRET - odrzucam webhook");
            ctx.json(500, Map.of("message", "webhook not configured"));
            return;
        }

        String signature = ctx.request().getHeader("Stripe-Signature");

        if (signature == null) {
            ctx.json(400, Map.of("message", "missing signature"));
            return;
        }

        // Podpis liczony jest z dokladnych bajtow body, wiec czytamy je bez zadnej obrobki
        String payload = new String(ctx.request().getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        Event event;

        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Webhook z nieprawidłowym podpisem");
            ctx.json(400, Map.of("message", "invalid signature"));
            return;
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> {
                String sessionId = extractSessionId(event);
                if (sessionId != null) {
                    orderService.handleCheckoutCompleted(sessionId);
                }
            }
            case "checkout.session.expired" -> {
                String sessionId = extractSessionId(event);
                if (sessionId != null) {
                    orderService.handleCheckoutExpired(sessionId);
                }
            }
            default -> log.debug("Webhook: pomijam zdarzenie {}", event.getType());
        }

        // 2xx = "odebralem"; przy wyjatku wyzej Stripe ponowi wysylke.
        ctx.json(200, Map.of("message", "ok"));
    }

    private String extractSessionId(Event event) {

        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        StripeObject object = deserializer.getObject().orElse(null);

        if (object == null) {
            // Wersja API zdarzenia rozni sie od wersji SDK
            try {
                object = deserializer.deserializeUnsafe();
            } catch (Exception e) {
                log.error("Nie udało się odczytać obiektu zdarzenia {}", event.getId(), e);
                return null;
            }
        }

        return object instanceof Session session ? session.getId() : null;
    }
}
