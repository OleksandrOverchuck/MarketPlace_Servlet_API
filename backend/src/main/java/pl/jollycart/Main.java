package pl.jollycart;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.jollycart.admin.AdminHandlers;
import pl.jollycart.admin.AdminInitializer;
import pl.jollycart.admin.AdminService;
import pl.jollycart.admin.AuditLogDao;
import pl.jollycart.admin.AuditService;
import pl.jollycart.admin.ContentCleanupService;
import pl.jollycart.admin.ModerationHandlers;
import pl.jollycart.admin.ModerationService;
import pl.jollycart.admin.StaffDao;
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
import pl.jollycart.file.ChatAttachmentValidator;
import pl.jollycart.file.CvFileService;
import pl.jollycart.file.FileService;
import pl.jollycart.file.ImageFileValidator;
import pl.jollycart.file.StoredFileDao;
import pl.jollycart.image.OfferImageDao;
import pl.jollycart.image.OfferImageHandlers;
import pl.jollycart.image.OfferImageService;
import pl.jollycart.job.JobApplicationDao;
import pl.jollycart.job.JobApplicationHandlers;
import pl.jollycart.job.JobApplicationService;
import pl.jollycart.message.ConversationDao;
import pl.jollycart.message.ConversationHandlers;
import pl.jollycart.message.ConversationService;
import pl.jollycart.message.MessageDao;
import pl.jollycart.message.MessageHandlers;
import pl.jollycart.message.MessageService;
import pl.jollycart.notification.MessageEmailListener;
import pl.jollycart.notification.NotificationDao;
import pl.jollycart.notification.NotificationHandlers;
import pl.jollycart.notification.NotificationService;
import pl.jollycart.notification.OrderPaidListener;
import pl.jollycart.offer.OfferDao;
import pl.jollycart.offer.OfferHandlers;
import pl.jollycart.offer.OfferService;
import pl.jollycart.payment.OrderDao;
import pl.jollycart.payment.OrderHandlers;
import pl.jollycart.payment.OrderService;
import pl.jollycart.payment.PaymentConfig;
import pl.jollycart.payment.PaymentScheduler;
import pl.jollycart.payment.PaymentWebhookHandlers;
import pl.jollycart.payment.PriceCalculator;
import pl.jollycart.payment.SellerAccountDao;
import pl.jollycart.payment.SellerAccountHandlers;
import pl.jollycart.payment.SellerAccountService;
import pl.jollycart.payment.StripeSetup;
import pl.jollycart.db.Db;
import pl.jollycart.rating.UserProfileHandlers;
import pl.jollycart.rating.UserRatingDao;
import pl.jollycart.rating.UserRatingService;
import pl.jollycart.user.PasswordService;
import pl.jollycart.user.UserDao;
import pl.jollycart.user.UserHandlers;
import pl.jollycart.user.UserService;
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

        // Platnosci Stripe: bez STRIPE_SECRET_KEY aplikacja dziala, ale bez platnosci
        PaymentConfig paymentConfig = PaymentConfig.load();
        StripeSetup.init(paymentConfig);

        // --- warstwa danych ---
        UserDao userDao = new UserDao();
        PendingRegistrationDao pendingDao = new PendingRegistrationDao();
        CategoryDao categoryDao = new CategoryDao();
        OfferDao offerDao = new OfferDao();
        OfferImageDao offerImageDao = new OfferImageDao();
        ConversationDao conversationDao = new ConversationDao();
        MessageDao messageDao = new MessageDao();
        NotificationDao notificationDao = new NotificationDao();
        StoredFileDao storedFileDao = new StoredFileDao();
        UserRatingDao userRatingDao = new UserRatingDao();
        JobApplicationDao jobApplicationDao = new JobApplicationDao();
        AuditLogDao auditLogDao = new AuditLogDao();
        StaffDao staffDao = new StaffDao();
        SellerAccountDao sellerAccountDao = new SellerAccountDao();
        OrderDao orderDao = new OrderDao();

        // --- logika biznesowa ---
        PasswordHasher passwordHasher = new PasswordHasher();
        EmailService emailService = new EmailService(config);
        AuthService authService = new AuthService(userDao, passwordHasher);
        RegistrationService registrationService = new RegistrationService(
                userDao, pendingDao, passwordHasher, emailService, config.verificationCodeLength());

        GoogleOAuth googleOAuth = new GoogleOAuth(
                config.googleClientId(), config.googleClientSecret());
        CategoryService categoryService = new CategoryService(categoryDao);
        UserService userService = new UserService(userDao);
        PasswordService passwordService = new PasswordService(userDao, passwordHasher);
        FileService fileService = new FileService(config.uploadDir());
        ImageFileValidator imageFileValidator = new ImageFileValidator();
        // Sprzedawca moze wlaczyc platnosc przez JollyCart dopiero, gdy Stripe potwierdzi jego konto wyplat.
        SellerAccountService sellerAccountService = new SellerAccountService(
                sellerAccountDao, config.frontendUrl(), paymentConfig.stripePublishableKey());
        OfferService offerService =
                new OfferService(offerDao, categoryDao, sellerAccountService::isPayoutReady);
        OfferImageService offerImageService =
                new OfferImageService(offerImageDao, offerDao, fileService, imageFileValidator);

        // --- dane poczatkowe (w Spring Boot: CommandLineRunner) ---
        categoryService.createInitialCategories();
        new AdminInitializer(userDao, passwordHasher,
                config.adminEmail(), config.adminNickname(), config.adminPassword()).run();

        // Pula watkow do zadan w tle (np. wysylka maili po zapisie wiadomosci).
        // Odpowiednik @EnableAsync / @Async w Spring Boot. Watki "daemon" nie blokuja zamkniecia aplikacji.
        ExecutorService asyncExecutor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "async-" + System.nanoTime());
            thread.setDaemon(true);
            return thread;
        });

        MessageEmailListener messageEmailListener =
                new MessageEmailListener(emailService, config.frontendUrl());
        NotificationService notificationService = new NotificationService(
                notificationDao, conversationDao, messageEmailListener, asyncExecutor);

        // Zamowienia i platnosci Stripe (potrzebuja asyncExecutor do maila po zatwierdzeniu transakcji)
        PriceCalculator priceCalculator = new PriceCalculator(paymentConfig);
        OrderPaidListener orderPaidListener = new OrderPaidListener(userDao, emailService, config.frontendUrl());
        OrderService orderService = new OrderService(
                offerDao, orderDao, userDao, sellerAccountService, sellerAccountDao, priceCalculator, paymentConfig,
                config.frontendUrl(), orderPaidListener, asyncExecutor);
        ConversationService conversationService = new ConversationService(conversationDao, userDao);
        MessageService messageService = new MessageService(
                messageDao, conversationDao, storedFileDao, notificationService,
                fileService, new ChatAttachmentValidator());

        UserRatingService userRatingService = new UserRatingService(userRatingDao, userDao, offerDao);
        JobApplicationService jobApplicationService = new JobApplicationService(
                jobApplicationDao, offerDao, new CvFileService(fileService, storedFileDao));

        AuditService auditService = new AuditService(auditLogDao);
        ContentCleanupService contentCleanupService = new ContentCleanupService(fileService);
        AdminService adminService =
                new AdminService(userDao, staffDao, contentCleanupService, auditService);
        ModerationService moderationService = new ModerationService(
                userDao, offerDao, staffDao, contentCleanupService, auditService);

        // --- warstwa HTTP ---
        Router router = new Router();
        new AuthHandlers(authService, registrationService).routes(router);
        new GoogleLoginHandlers(googleOAuth, authService, config.frontendUrl()).routes(router);
        new CategoryHandlers(categoryService).routes(router);
        new UserHandlers(userService, passwordService, fileService, imageFileValidator, userDao)
                .routes(router);
        new OfferHandlers(offerService).routes(router);
        new OfferImageHandlers(offerImageService, fileService).routes(router);
        new ConversationHandlers(conversationService).routes(router);
        new MessageHandlers(messageService).routes(router);
        new NotificationHandlers(notificationService).routes(router);
        new UserProfileHandlers(userRatingService).routes(router);
        new JobApplicationHandlers(jobApplicationService).routes(router);
        new AdminHandlers(adminService, auditService).routes(router);
        new ModerationHandlers(moderationService).routes(router);
        new SellerAccountHandlers(sellerAccountService).routes(router);
        new OrderHandlers(orderService).routes(router);
        new PaymentWebhookHandlers(orderService, paymentConfig.stripeWebhookSecret()).routes(router);

        Tomcat tomcat = EmbeddedServer.start(config, router, userDao);

        // Terminy platnosci (wygasanie, zwrot za brak wysylki, auto-odbior, auto-wyplata)
        PaymentScheduler paymentScheduler = new PaymentScheduler(orderDao, orderService, paymentConfig);
        paymentScheduler.start();

        log.info("Serwer uruchomiony: http://localhost:{}", config.serverPort());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            paymentScheduler.stop();
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (Exception e) {
                log.warn("Blad przy zatrzymywaniu serwera", e);
            }
            asyncExecutor.shutdown();
            Db.close();
        }));

        tomcat.getServer().await();
    }
}
