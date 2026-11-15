package pl.jollycart.job;

import java.time.LocalDateTime;

public record JobApplicationResponse(
        Long id,
        Long offerId,
        String offerTitle,
        Long userId,
        String userNickname,
        JobApplicationStatus status,
        String message,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
