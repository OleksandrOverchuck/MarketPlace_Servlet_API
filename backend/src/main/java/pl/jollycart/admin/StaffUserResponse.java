package pl.jollycart.admin;

import java.time.LocalDateTime;

import pl.jollycart.user.AccountStatus;
import pl.jollycart.user.AuthProvider;
import pl.jollycart.user.Role;

public record StaffUserResponse(
        Long id,
        String nickname,
        String email,
        Role role,
        AccountStatus status,
        AuthProvider authProvider,
        String avatarUrl,
        String banReason,
        LocalDateTime bannedAt,
        LocalDateTime createdAt,
        long offersCount
) {
}
