package pl.jollycart.admin;

import java.util.List;

import pl.jollycart.user.User;

/** Dziennik akcji administracyjnych (kto, co, kiedy). */
public final class AuditService {

    private static final int MAX_DETAILS_LENGTH = 1000;

    private final AuditLogDao auditLogDao;

    public AuditService(AuditLogDao auditLogDao) {
        this.auditLogDao = auditLogDao;
    }

    public void log(User actor, AuditAction action, String targetType, Long targetId, String details) {
        String safeDetails = details;
        if (safeDetails != null && safeDetails.length() > MAX_DETAILS_LENGTH) {
            safeDetails = safeDetails.substring(0, MAX_DETAILS_LENGTH);
        }

        auditLogDao.insert(actor.id(), actor.nickname(), action, targetType, targetId, safeDetails);
    }

    public PageResponse<AuditLogResponse> list(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        List<AuditLogResponse> items = auditLogDao.findPage(safePage, safeSize);
        return PageResponse.of(items, safePage, safeSize, auditLogDao.count());
    }
}
