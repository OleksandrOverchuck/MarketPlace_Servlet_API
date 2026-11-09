package pl.jollycart.auth;

import java.time.LocalDateTime;

/** Rejestracja w toku - konto powstaje dopiero po wpisaniu kodu z e-maila. */
public record PendingRegistration(
        Long id,
        String email,
        String nickname,
        String passwordHash,
        String codeHash,
        LocalDateTime expiresAt,
        LocalDateTime lastSentAt,
        int attempts,
        LocalDateTime createdAt
) {
}
