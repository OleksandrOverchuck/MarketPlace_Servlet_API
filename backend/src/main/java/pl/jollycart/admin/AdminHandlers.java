package pl.jollycart.admin;

import java.io.IOException;

import pl.jollycart.web.Ctx;
import pl.jollycart.web.QueryParams;
import pl.jollycart.web.Router;

/** Endpointy /api/admin (dostep tylko dla ADMIN - zob. AuthorizationFilter). */
public final class AdminHandlers {

    private final AdminService adminService;
    private final AuditService auditService;

    public AdminHandlers(AdminService adminService, AuditService auditService) {
        this.adminService = adminService;
        this.auditService = auditService;
    }

    public void routes(Router router) {
        router.delete("/api/admin/users/{id}", this::deleteUser);
        router.put("/api/admin/users/{id}/role", this::changeRole);
        router.get("/api/admin/audit-log", this::auditLog);
    }

    private void deleteUser(Ctx ctx) {
        adminService.deleteUser(ctx.currentUser(), ctx.pathLong("id"));
        ctx.noContent();
    }

    private void changeRole(Ctx ctx) throws IOException {
        long id = ctx.pathLong("id");
        ChangeRoleRequest request = ctx.bodyWithMessage(ChangeRoleRequest.class);
        ctx.json(200, adminService.changeRole(ctx.currentUser(), id, request.role()));
    }

    private void auditLog(Ctx ctx) throws IOException {
        ctx.json(200, auditService.list(
                QueryParams.intParam(ctx, "page", 0),
                QueryParams.intParam(ctx, "size", 30)));
    }
}
