package pl.jollycart.admin;

import java.util.List;

import pl.jollycart.db.Db;
import pl.jollycart.offer.Offer;
import pl.jollycart.offer.OfferDao;
import pl.jollycart.offer.OfferStatus;
import pl.jollycart.user.AccountStatus;
import pl.jollycart.user.Role;
import pl.jollycart.user.User;
import pl.jollycart.user.UserDao;
import pl.jollycart.web.StatusException;

/**
 * Operacje dla MODERATORA i ADMINA: wyszukiwanie uzytkownikow, ban / unban,
 * wyszukiwanie i usuwanie ogloszen (odpowiednik ModerationService).
 */
public final class ModerationService {

    private final UserDao userDao;
    private final OfferDao offerDao;
    private final StaffDao staffDao;
    private final ContentCleanupService contentCleanupService;
    private final AuditService auditService;

    public ModerationService(UserDao userDao, OfferDao offerDao, StaffDao staffDao,
                             ContentCleanupService contentCleanupService, AuditService auditService) {
        this.userDao = userDao;
        this.offerDao = offerDao;
        this.staffDao = staffDao;
        this.contentCleanupService = contentCleanupService;
        this.auditService = auditService;
    }

    /* ------------------------------ uzytkownicy ------------------------------ */

    public PageResponse<StaffUserResponse> searchUsers(String q, Role role, AccountStatus status,
                                                       int page, int size) {
        String term = (q == null || q.isBlank()) ? null : q.trim();
        int safePage = clampPage(page);
        int safeSize = clampSize(size);

        List<StaffUserResponse> items = staffDao.searchUsers(term, role, status, safePage, safeSize);
        return PageResponse.of(items, safePage, safeSize, staffDao.countUsers(term, role, status));
    }

    public StaffUserResponse getUser(long id) {
        return staffDao.findUserById(id)
                .orElseThrow(() -> StatusException.notFound("Użytkownik nie został znaleziony"));
    }

    public StaffUserResponse banUser(User actor, long targetId, String reason) {
        return Db.inTransaction(() -> {
            User target = findUser(targetId);
            assertCanModerate(actor, target);

            if (target.isBanned()) {
                throw StatusException.conflict("Użytkownik jest już zablokowany");
            }

            staffDao.ban(target.id(), reason.trim(), actor.id());

            auditService.log(actor, AuditAction.USER_BANNED, "USER", target.id(),
                    "Ban użytkownika " + target.nickname() + " (" + target.email()
                            + "). Powód: " + reason.trim());

            return getUser(target.id());
        });
    }

    public StaffUserResponse unbanUser(User actor, long targetId) {
        return Db.inTransaction(() -> {
            User target = findUser(targetId);
            assertCanModerate(actor, target);

            if (!target.isBanned()) {
                throw StatusException.conflict("Użytkownik nie jest zablokowany");
            }

            staffDao.unban(target.id());

            auditService.log(actor, AuditAction.USER_UNBANNED, "USER", target.id(),
                    "Odblokowano użytkownika " + target.nickname() + " (" + target.email() + ")");

            return getUser(target.id());
        });
    }

    /* ------------------------------ ogloszenia ------------------------------ */

    public PageResponse<StaffOfferResponse> searchOffers(String q, Long userId, OfferStatus status,
                                                         int page, int size) {
        String term = (q == null || q.isBlank()) ? null : q.trim();
        int safePage = clampPage(page);
        int safeSize = clampSize(size);

        List<StaffOfferResponse> items = staffDao.searchOffers(term, userId, status, safePage, safeSize);
        return PageResponse.of(items, safePage, safeSize, staffDao.countOffers(term, userId, status));
    }

    public void deleteOffer(User actor, long offerId, String reason) {
        Db.inTransaction(() -> {
            Offer offer = offerDao.findById(offerId)
                    .orElseThrow(() -> StatusException.notFound("Ogłoszenie nie zostało znalezione"));

            User owner = userDao.findById(offer.userId()).orElseThrow();

            if (owner.role() == Role.ADMIN && actor.role() != Role.ADMIN) {
                throw StatusException.forbidden("Nie możesz usuwać ogłoszeń administratora");
            }

            String details = "Usunięto ogłoszenie \"" + offer.title() + "\" użytkownika "
                    + owner.nickname() + " (id " + owner.id() + ")";

            if (reason != null && !reason.isBlank()) {
                details += ". Powód: " + reason.trim();
            }

            auditService.log(actor, AuditAction.OFFER_DELETED, "OFFER", offerId, details);

            contentCleanupService.deleteOffer(offerId);
        });
    }

    /* ------------------------------ pomocnicze ------------------------------ */

    private void assertCanModerate(User actor, User target) {
        if (actor.id().equals(target.id())) {
            throw StatusException.forbidden("Nie możesz wykonać tej akcji na własnym koncie");
        }

        if (target.role() == Role.ADMIN) {
            throw StatusException.forbidden("Nie można moderować administratora");
        }

        if (target.role() == Role.MODERATOR && actor.role() != Role.ADMIN) {
            throw StatusException.forbidden("Tylko administrator może moderować moderatora");
        }
    }

    private User findUser(long id) {
        return userDao.findById(id)
                .orElseThrow(() -> StatusException.notFound("Użytkownik nie został znaleziony"));
    }

    private static int clampPage(int page) {
        return Math.max(page, 0);
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
