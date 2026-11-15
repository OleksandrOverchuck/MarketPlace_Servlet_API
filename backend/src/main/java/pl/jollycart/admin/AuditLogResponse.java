package pl.jollycart.admin;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long actorId,
        String actorNickname,
        AuditAction action,
        String targetType,
        Long targetId,
        String details,
        LocalDateTime createdAt
) {
}
