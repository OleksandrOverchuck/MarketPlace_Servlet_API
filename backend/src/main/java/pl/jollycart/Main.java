package pl.jollycart;

import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.jollycart.admin.AdminInitializer;
import pl.jollycart.auth.AuthHandlers;
import pl.jollycart.auth.AuthService;
import pl.jollycart.auth.EmailService;
import pl.jollycart.auth.GoogleLoginHandlers;
import pl.jollycart.auth.GoogleOAuth;
import pl.jollycart.auth.PasswordHasher;
import pl.jollycart.auth.PendingRegistrationDao;
import pl.jollycart.auth.RegistrationService;
import pl.jollycart.category.CategoryDao;
import pl.jollycart.category.CategoryHandlers;
import pl.jollycart.category.CategoryService;
import pl.jollycart.config.AppConfig;
import pl.jollycart.db.Db;
import pl.jollycart.user.UserDao;
import pl.jollycart.web.EmbeddedServer;
import pl.jollycart.web.Router;

/**
 * Punkt startowy aplikacji. Zaleznosci tworzymy i laczymy recznie
 * (w Spring Boot robi to kontener Inversion of Control).
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.load();

        Db.init(config);

        // --- warstwa danych ---
        UserDao userDao = new UserDao();
        PendingRegistrationDao pendingDao = new PendingRegistrationDao();
        CategoryDao categoryDao = new CategoryDao();

        // --- logika biznesowa ---
        PasswordHasher passwordHasher = new PasswordHasher();
        EmailService emailService = new EmailService(config);
        AuthService authService = new AuthService(userDao, passwordHasher);
        RegistrationService registrationService = new RegistrationService(
                userDao, pendingDao, passwordHasher, emailService, config.verificationCodeLength());

        GoogleOAuth googleOAuth = new GoogleOAuth(
                config.googleClientId(), config.googleClientSecret());
        CategoryService categoryService = new CategoryService(categoryDao);

        // --- dane poczatkowe (w Spring Boot: CommandLineRunner) ---
        categoryService.createInitialCategories();
        new AdminInitializer(userDao, passwordHasher,
                config.adminEmail(), config.adminNickname(), config.adminPassword()).run();

        // --- warstwa HTTP ---
        Router router = new Router();
        new AuthHandlers(authService, registrationService).routes(router);
        new GoogleLoginHandlers(googleOAuth, authService, config.frontendUrl()).routes(router);
        new CategoryHandlers(categoryService).routes(router);

        Tomcat tomcat = EmbeddedServer.start(config, router, userDao);
        log.info("Serwer uruchomiony: http://localhost:{}", config.serverPort());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (Exception e) {
                log.warn("Blad przy zatrzymywaniu serwera", e);
            }
            Db.close();
        }));

        tomcat.getServer().await();
    }
}
