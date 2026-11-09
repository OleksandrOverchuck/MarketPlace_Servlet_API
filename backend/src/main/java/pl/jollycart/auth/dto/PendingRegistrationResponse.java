package pl.jollycart.auth.dto;

public record PendingRegistrationResponse(
        String email,
        long codeValidSeconds,
        long resendCooldownSeconds
) {
}
