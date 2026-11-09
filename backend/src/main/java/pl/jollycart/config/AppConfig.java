package pl.jollycart.config;

import java.util.Set;

/**
 * Konfiguracja aplikacji. Odpowiednik application.properties ze Spring Boot.
 * Wartosci pochodza ze zmiennych srodowiskowych (te same nazwy co w wersji Spring:
 * CON_PASSWORD, MAIL_USERNAME, MAIL_PASSWORD), z wartosciami domyslnymi.
 */
public record AppConfig(
        int serverPort,
        String dbUrl,
        String dbUser,
        String dbPassword,
        int dbPoolSize,
        String mailHost,
        int mailPort,
        String mailUsername,
        String mailPassword,
        String mailFrom,
        int verificationCodeLength,
        Set<String> corsAllowedOrigins,
        String frontendUrl,
        String googleClientId,
        String googleClientSecret
) {

    public static AppConfig load() {
        String mailUsername = env("MAIL_USERNAME", "");

        return new AppConfig(
                Integer.parseInt(env("SERVER_PORT", "8080")),
                env("DB_URL", "jdbc:mysql://localhost:3306/jollycart_servlet"
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Europe/Warsaw"),
                env("DB_USER", "JollyCart_User"),
                required("CON_PASSWORD"),
                10,                                   // tyle samo co domyslna pula Hikari w Spring Boot
                "smtp.gmail.com",
                587,
                mailUsername,
                env("MAIL_PASSWORD", ""),
                mailUsername,                         // app.mail.from=${MAIL_USERNAME}
                6,                                    // app.verification.code-length
                Set.of("http://localhost:5501", "http://127.0.0.1:5501"),
                env("APP_FRONTEND_URL", "http://localhost:5501"),
                env("GOOGLE_CLIENT_ID",
                        "529987822035-r69u35gj9m6lfdokqc0f4tkoe8uc87cq.apps.googleusercontent.com"),
                env("GOOGLE_CLIENT_SECRET", "")
        );
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Brak zmiennej srodowiskowej " + name + " (haslo do bazy danych)");
        }
        return value;
    }
}
