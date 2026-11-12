package pl.jollycart.admin;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.jollycart.auth.PasswordHasher;
import pl.jollycart.user.Role;
import pl.jollycart.user.User;
import pl.jollycart.user.UserDao;

/**
 * Przy starcie aplikacji tworzy konto administratora, jesli w bazie
 * nie ma jeszcze zadnego ADMINA.
 *
 *  - dane konta: ADMIN_EMAIL / ADMIN_NICKNAME / ADMIN_PASSWORD (zmienne srodowiskowe),
 *  - brak ADMIN_PASSWORD -> haslo startowe z DEFAULT_PASSWORD, a konto
 *    musi je zmienic przy pierwszym logowaniu (panel jest zablokowany
 *    do czasu zmiany hasla).
 */
public final class AdminInitializer {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    /** Haslo startowe - ZMIEN JE po pierwszym zalogowaniu. */
    public static final String DEFAULT_PASSWORD = "Admin12345";

    private static final int MAX_NICKNAME_LENGTH = 30;

    private final UserDao userDao;
    private final PasswordHasher passwordHasher;
    private final String adminEmail;
    private final String adminNickname;
    private final String adminPassword;

    public AdminInitializer(UserDao userDao, PasswordHasher passwordHasher,
                            String adminEmail, String adminNickname, String adminPassword) {
        this.userDao = userDao;
        this.passwordHasher = passwordHasher;
        this.adminEmail = adminEmail;
        this.adminNickname = adminNickname;
        this.adminPassword = adminPassword;
    }

    public void run() {
        if (userDao.existsByRole(Role.ADMIN)) {
            return;
        }

        // konto z tym emailem juz istnieje - nadajemy mu role ADMIN
        Optional<User> existing = userDao.findByEmail(adminEmail);
        if (existing.isPresent()) {
            userDao.changeRole(existing.get().id(), Role.ADMIN);
            log.warn("Konto {} już istniało - nadano mu rolę ADMIN (hasło bez zmian).", adminEmail);
            return;
        }

        boolean usingDefault = adminPassword == null || adminPassword.isBlank();

        if (!usingDefault && adminPassword.length() < 8) {
            throw new IllegalStateException("ADMIN_PASSWORD musi mieć co najmniej 8 znaków");
        }

        String rawPassword = usingDefault ? DEFAULT_PASSWORD : adminPassword;

        userDao.createAdmin(
                uniqueNickname(adminNickname),
                adminEmail,
                passwordHasher.encode(rawPassword),
                usingDefault
        );

        if (usingDefault) {
            log.warn("""

                    ==================================================
                     UTWORZONO KONTO ADMINISTRATORA
                       email: {}
                       hasło: {}
                     Hasło tymczasowe - zostaniesz poproszony o jego
                     zmianę przy pierwszym zalogowaniu.
                    ==================================================
                    """,
                    adminEmail,
                    rawPassword);
        } else {
            log.info("Utworzono konto administratora {} (hasło z ADMIN_PASSWORD).", adminEmail);
        }
    }

    private String uniqueNickname(String base) {
        String candidate = base;
        int counter = 1;

        while (userDao.existsByNickname(candidate)) {
            String suffix = "_" + counter++;
            candidate = base.substring(0, Math.min(base.length(), MAX_NICKNAME_LENGTH - suffix.length()))
                    + suffix;
        }

        return candidate;
    }
}
