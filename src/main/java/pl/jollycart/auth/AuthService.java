package pl.jollycart.auth;

import java.util.Optional;

import pl.jollycart.db.Db;
import pl.jollycart.user.AuthProvider;
import pl.jollycart.user.User;
import pl.jollycart.user.UserDao;

/** Logowanie hasłem i przez Google (odpowiednik AuthService + DaoAuthenticationProvider). */
public final class AuthService {

    /** Hash "na probe" - wyrownuje czas odpowiedzi, gdy konto nie istnieje (ochrona przed enumeracja). */
    private static final String DUMMY_HASH = new PasswordHasher().encode("dummy-password");

    private static final int MAX_NICKNAME_LENGTH = 30;

    private final UserDao userDao;
    private final PasswordHasher passwordHasher;

    public AuthService(UserDao userDao, PasswordHasher passwordHasher) {
        this.userDao = userDao;
        this.passwordHasher = passwordHasher;
    }

    /**
     * Sprawdza email i haslo. Zwraca uzytkownika albo rzuca:
     *  - AuthenticationFailedException (zle dane, rowniez dla kont tylko-Google bez hasla),
     *  - AccountBannedException (konto zablokowane).
     */
    public User authenticate(String email, String password) {
        Optional<User> found = userDao.findByEmail(email);
        String hash = found.map(User::passwordHash).orElse(null);

        if (hash == null) {
            passwordHasher.matches(password, DUMMY_HASH);
            throw new AuthenticationFailedException();
        }

        if (!passwordHasher.matches(password, hash)) {
            throw new AuthenticationFailedException();
        }

        User user = found.get();

        // Ban sprawdzamy DOPIERO po poprawnym hasle, zeby nie ujawniac
        // osobom bez hasla, ze dane konto jest zablokowane.
        if (user.isBanned()) {
            throw new AccountBannedException(user.banReason());
        }

        return user;
    }

    /* ------------------------------------------------------------------
     * Logowanie / rejestracja przez Google
     * ------------------------------------------------------------------ */

    /**
     * 1) uzytkownik, ktory juz logowal sie przez Google -> zwracamy go,
     * 2) konto zalozone recznie z tym samym emailem -> podpinamy do niego Google,
     * 3) w przeciwnym razie tworzymy nowe konto (bez hasla).
     */
    public User getOrCreateGoogleUser(String email, String providerId,
                                      String googleNickname, String avatarUrl) {
        return Db.inTransaction(() -> {
            Optional<User> byProvider =
                    userDao.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, providerId);
            if (byProvider.isPresent()) {
                return byProvider.get();
            }

            Optional<User> byEmail = userDao.findByEmail(email);
            if (byEmail.isPresent()) {
                return linkGoogleAccount(byEmail.get(), providerId, avatarUrl);
            }

            String nickname = createUniqueNickname(googleNickname, email);
            long id = userDao.createGoogle(nickname, email, providerId, avatarUrl);
            return userDao.findById(id).orElseThrow();
        });
    }

    private User linkGoogleAccount(User existing, String providerId, String avatarUrl) {
        // Email jest juz powiazany z innym kontem Google - nie nadpisujemy
        if (existing.providerId() != null && !existing.providerId().equals(providerId)) {
            throw new IllegalArgumentException(
                    "Konto z tym adresem email jest powiązane z innym kontem Google");
        }

        // authProvider zostaje bez zmian (LOCAL), dzieki temu logowanie haslem
        // i zmiana hasla dzialaja nadal obok logowania przez Google
        userDao.linkGoogle(existing.id(), providerId, avatarUrl);
        return userDao.findById(existing.id()).orElseThrow();
    }

    private String createUniqueNickname(String googleNickname, String email) {
        String base = googleNickname;

        if (base == null || base.isBlank()) {
            base = email.substring(0, email.indexOf('@'));
        }

        base = base.trim().replaceAll("\\s+", "_");

        if (base.length() > MAX_NICKNAME_LENGTH) {
            base = base.substring(0, MAX_NICKNAME_LENGTH);
        }

        if (!userDao.existsByNickname(base)) {
            return base;
        }

        for (int counter = 1; ; counter++) {
            String suffix = "_" + counter;
            int maxBaseLength = MAX_NICKNAME_LENGTH - suffix.length();
            String shortened = base.length() > maxBaseLength ? base.substring(0, maxBaseLength) : base;
            String candidate = shortened + suffix;

            if (!userDao.existsByNickname(candidate)) {
                return candidate;
            }
        }
    }
}
