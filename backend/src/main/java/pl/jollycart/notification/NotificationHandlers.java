package pl.jollycart.notification;

import java.io.IOException;

import pl.jollycart.web.Ctx;
import pl.jollycart.web.Router;

/** Endpointy /api/notifications (odpowiednik NotificationController). */
public final class NotificationHandlers {

    private final NotificationService notificationService;

    public NotificationHandlers(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void routes(Router router) {
        router.get("/api/notifications", this::list);
        router.get("/api/notifications/unread-count", this::unreadCount);
        router.patch("/api/notifications/{id}/read", this::markAsRead);
        router.post("/api/notifications/read-all", this::markAllAsRead);
    }

    private void list(Ctx ctx) throws IOException {
        ctx.json(200, notificationService.getUserNotifications(ctx.currentUser()));
    }

    private void unreadCount(Ctx ctx) throws IOException {
        ctx.json(200, notificationService.getUnreadCount(ctx.currentUser()));
    }

    private void markAsRead(Ctx ctx) {
        notificationService.markAsRead(ctx.pathLong("id"), ctx.currentUser());
        ctx.noContent();
    }

    private void markAllAsRead(Ctx ctx) {
        notificationService.markAllAsRead(ctx.currentUser());
        ctx.noContent();
    }
}
