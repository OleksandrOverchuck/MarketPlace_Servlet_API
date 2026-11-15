package pl.jollycart.admin;

import pl.jollycart.db.Db;
import pl.jollycart.user.Role;
import pl.jollycart.user.User;
import pl.jollycart.user.UserDao;
import pl.jollycart.web.StatusException;

/** Operacje tylko dla ADMINA: trwale usuwanie kont i nadawanie rol (odpowiednik AdminService). */
public final class AdminService {

    private final UserDao userDao;
    private final StaffDao staffDao;
    private final ContentCleanupService contentCleanupService;
    private final AuditService auditService;

    public AdminService(UserDao userDao, StaffDao staffDao,
                        ContentCleanupService contentCleanupService, AuditService auditService) {
        this.userDao = userDao;
        this.staffDao = staffDao;
        this.contentCleanupService = contentCleanupService;
        this.auditService = auditService;
    }

    public void deleteUser(User actor, long targetId) {
        Db.inTransaction(() -> {
            User target = findUser(targetId);
            assertTargetIsManageable(actor, target);

            String details = "Trwale usunięto konto " + target.nickname()
                    + " (" + target.email() + ", id " + target.id()
                    + ", rola " + target.role() + ")";

            auditService.log(actor, AuditAction.USER_DELETED, "USER", target.id(), details);

            contentCleanupService.deleteUser(target);
        });
    }

    public StaffUserResponse changeRole(User actor, long targetId, Role newRole) {
        return Db.inTransaction(() -> {
            User target = findUser(targetId);
            assertTargetIsManageable(actor, target);

            // Role ADMIN ma wylacznie konto tworzone przy starcie aplikacji.
            if (newRole != Role.USER && newRole != Role.MODERATOR) {
                throw StatusException.forbidden("Można nadać tylko rolę USER lub MODERATOR");
            }

            Role oldRole = target.role();

            if (oldRole != newRole) {
                userDao.changeRole(target.id(), newRole);

                auditService.log(actor, AuditAction.ROLE_CHANGED, "USER", target.id(),
                        "Zmiana roli użytkownika " + target.nickname()
                                + " (id " + target.id() + "): " + oldRole + " -> " + newRole);
            }

            return staffDao.findUserById(target.id()).orElseThrow();
        });
    }

    private void assertTargetIsManageable(User actor, User target) {
        if (actor.id().equals(target.id())) {
            throw StatusException.forbidden("Nie możesz wykonać tej akcji na własnym koncie");
        }

        if (target.role() == Role.ADMIN) {
            throw StatusException.forbidden("Nie można zmieniać ani usuwać konta administratora");
        }
    }

    private User findUser(long id) {
        return userDao.findById(id)
                .orElseThrow(() -> StatusException.notFound("Użytkownik nie został znaleziony"));
    }
}
