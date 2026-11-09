package pl.jollycart.user;

import java.time.LocalDateTime;

/** Wiersz tabeli users. Niemutowalny - zmiany robimy zapytaniami UPDATE w DAO. */
public record User(
        Long id,
        String nickname,
        String email,
        String passwordHash,
        AuthProvider authProvider,
        String providerId,
        String avatarUrl,
        String phone,
        String location,
        Role role,
        AccountStatus status,
        String banReason,
        LocalDateTime bannedAt,
        Long bannedById,
        boolean passwordChangeRequired,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public boolean isBanned() {
        return status == AccountStatus.BANNED;
    }
}
