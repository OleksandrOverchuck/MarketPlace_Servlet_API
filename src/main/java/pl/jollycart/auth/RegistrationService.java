package pl.jollycart.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import pl.jollycart.auth.dto.PendingRegistrationResponse;
import pl.jollycart.auth.dto.RegisterRequest;
import pl.jollycart.db.Db;
import pl.jollycart.user.User;
import pl.jollycart.user.UserDao;
import pl.jollycart.user.UserResponse;

/**
 * Dwuetapowa rejestracja kontem lokalnym:
 * 1) startRegistration - walidacja + wysłanie kodu na email,
 * 2) verify            - sprawdzenie kodu i dopiero wtedy utworzenie konta.
 */
public final class RegistrationService {

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration PENDING_RETENTION = Duration.ofHours(24);
    private static final int MAX_ATTEMPTS = 5;

    private final UserDao userDao;
    private final PendingRegistrationDao pendingDao;
    private final PasswordHasher passwordHasher;
    private final EmailService emailService;
    private final int codeLength;
    private final SecureRandom random = new SecureRandom();

    public RegistrationService(
            UserDao userDao,
            PendingRegistrationDao pendingDao,
            PasswordHasher passwordHasher,
            EmailService emailService,
            int codeLength
    ) {
        this.userDao = userDao;
        this.pendingDao = pendingDao;
        this.passwordHasher = passwordHasher;
        this.emailService = emailService;
        this.codeLength = codeLength;
    }

    /* ---------- krok 1: formularz rejestracji ---------- */

    public PendingRegistrationResponse startRegistration(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Hasła nie są zgodne");
        }

        String email = request.email().trim();
        String nickname = request.nickname().trim();

        // Cała operacja w jednej transakcji: jeśli wysyłka maila się nie uda,
        // wyjątek cofa zapis do bazy.
        return Db.inTransaction(() -> {
            // sprzątanie bardzo starych, porzuconych rejestracji
            pendingDao.deleteByCreatedAtBefore(LocalDateTime.now().minus(PENDING_RETENTION));

            if (userDao.existsByEmail(email)) {
                throw new IllegalArgumentException("Email jest już zajęty");
            }

            if (userDao.existsByNickname(nickname)
                    || pendingDao.existsByNicknameAndEmailNot(nickname, email)) {
                throw new IllegalArgumentException("Nickname jest już zajęty");
            }

            // ten sam email może zacząć rejestrację ponownie
            // (np. zapomniał kodu) - nadpisujemy poprzednią próbę
            Optional<PendingRegistration> existing = pendingDao.findByEmail(email);
            existing.ifPresent(this::ensureCooldownPassed);

            String code = generateCode();
            LocalDateTime now = LocalDateTime.now();
            String passwordHash = passwordHasher.encode(request.password());
            String codeHash = passwordHasher.encode(code);
            LocalDateTime expiresAt = now.plus(CODE_TTL);

            if (existing.isPresent()) {
                pendingDao.restart(existing.get().id(), email, nickname,
                        passwordHash, codeHash, expiresAt, now);
            } else {
                pendingDao.insert(email, nickname, passwordHash, codeHash, expiresAt, now);
            }

            emailService.sendVerificationCode(email, code, CODE_TTL.toMinutes());

            return response(email);
        });
    }

    /* ---------- krok 2: wpisanie kodu ---------- */

    /**
     * Celowo BEZ jednej transakcji na całą metodę: licznik błędnych prób
     * (i usunięcie nieaktualnej rejestracji) musi zapisać się w bazie mimo
     * rzucanego wyjątku. W Springu robi to noRollbackFor; tutaj w transakcji
     * jest tylko końcowe utworzenie konta.
     */
    public UserResponse verify(String rawEmail, String code) {
        String email = rawEmail.trim();

        PendingRegistration pending = pendingDao.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nie znaleziono rejestracji dla tego adresu. Zarejestruj się ponownie."));

        if (pending.attempts() >= MAX_ATTEMPTS) {
            throw new IllegalArgumentException("Zbyt wiele błędnych prób. Wyślij nowy kod.");
        }

        if (pending.expiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Kod wygasł. Wyślij nowy kod.");
        }

        if (!passwordHasher.matches(code, pending.codeHash())) {
            int attempts = pending.attempts() + 1;
            pendingDao.updateAttempts(pending.id(), attempts);

            int left = MAX_ATTEMPTS - attempts;
            throw new IllegalArgumentException(
                    left > 0
                            ? "Nieprawidłowy kod. Pozostało prób: " + left
                            : "Zbyt wiele błędnych prób. Wyślij nowy kod.");
        }

        // kod OK - jeszcze raz sprawdzamy unikalność (ktoś mógł
        // w międzyczasie zająć email/nick, np. przez Google)
        if (userDao.existsByEmail(pending.email())) {
            pendingDao.delete(pending.id());
            throw new IllegalArgumentException("Email jest już zajęty");
        }

        if (userDao.existsByNickname(pending.nickname())) {
            pendingDao.delete(pending.id());
            throw new IllegalArgumentException(
                    "Nickname został w międzyczasie zajęty. Zarejestruj się ponownie.");
        }

        long userId = Db.inTransaction(() -> {
            long id = userDao.createLocal(
                    pending.nickname(), pending.email(), pending.passwordHash());
            pendingDao.delete(pending.id());
            return id;
        });

        User saved = userDao.findById(userId).orElseThrow();
        return UserResponse.from(saved);
    }

    /* ---------- ponowne wysłanie kodu ---------- */

    public PendingRegistrationResponse resendCode(String rawEmail) {
        String email = rawEmail.trim();

        return Db.inTransaction(() -> {
            PendingRegistration pending = pendingDao.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Nie znaleziono rejestracji dla tego adresu. Zarejestruj się ponownie."));

            ensureCooldownPassed(pending);

            String code = generateCode();
            LocalDateTime now = LocalDateTime.now();

            pendingDao.updateCode(pending.id(), passwordHasher.encode(code), now.plus(CODE_TTL), now);
            emailService.sendVerificationCode(email, code, CODE_TTL.toMinutes());

            return response(email);
        });
    }

    /* ---------- pomocnicze ---------- */

    private void ensureCooldownPassed(PendingRegistration pending) {
        if (pending.lastSentAt() == null) {
            return;
        }

        long secondsLeft = Duration
                .between(LocalDateTime.now(), pending.lastSentAt().plus(RESEND_COOLDOWN))
                .getSeconds();

        if (secondsLeft > 0) {
            throw new IllegalArgumentException(
                    "Odczekaj " + secondsLeft + " s przed wysłaniem kolejnego kodu.");
        }
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, codeLength);
        return String.format("%0" + codeLength + "d", random.nextInt(bound));
    }

    private PendingRegistrationResponse response(String email) {
        return new PendingRegistrationResponse(
                email,
                CODE_TTL.getSeconds(),
                RESEND_COOLDOWN.getSeconds()
        );
    }
}
