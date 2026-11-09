package pl.jollycart.auth;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import pl.jollycart.user.User;
import pl.jollycart.web.Ctx;
import pl.jollycart.web.Router;

/**
 * Logowanie i rejestracja przez Google. Te same adresy co w Spring Security:
 *  - GET /oauth2/authorization/google       - start (przekierowanie do Google),
 *  - GET /login/oauth2/code/google          - powrot z Google (adres zarejestrowany w konsoli Google).
 * Po sukcesie uzytkownik trafia na index.html, po bledzie na login.html?error=<kod>.
 */
public final class GoogleLoginHandlers {

    private static final Logger log = LoggerFactory.getLogger(GoogleLoginHandlers.class);

    private static final String START_PATH = "/oauth2/authorization/google";
    private static final String CALLBACK_PATH = "/login/oauth2/code/google";

    private static final String ATTR_STATE = "jollycart.oauth.state";
    private static final String ATTR_NONCE = "jollycart.oauth.nonce";

    private final GoogleOAuth google;
    private final AuthService authService;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public GoogleLoginHandlers(GoogleOAuth google, AuthService authService, String frontendUrl) {
        this.google = google;
        this.authService = authService;
        this.frontendUrl = frontendUrl;
    }

    public void routes(Router router) {
        router.get(START_PATH, this::start);
        router.get(CALLBACK_PATH, this::callback);
    }

    /** Krok 1: zapamietujemy state i nonce w sesji i odsylamy uzytkownika do Google. */
    private void start(Ctx ctx) throws IOException {
        if (!google.isConfigured()) {
            log.error("Brak GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET - logowanie przez Google nie dziala");
            redirectWithError(ctx, "oauth_failed");
            return;
        }

        String state = randomToken();
        String nonce = randomToken();

        HttpSession session = ctx.request().getSession(true);
        session.setAttribute(ATTR_STATE, state);
        session.setAttribute(ATTR_NONCE, nonce);

        ctx.redirect(google.authorizationUrl(redirectUri(ctx.request()), state, nonce));
    }

    /** Krok 2: Google odsyla uzytkownika z kodem - konczymy logowanie. */
    private void callback(Ctx ctx) throws IOException {
        try {
            User user = completeLogin(ctx.request());

            SessionAuth.login(ctx.request(), user);
            ctx.redirect(frontendUrl + "/frontend/pages/index.html");

        } catch (OAuthException e) {
            log.info("Logowanie przez Google odrzucone: {}", e.getCode());
            redirectWithError(ctx, e.getCode());

        } catch (Exception e) {
            log.error("Blad logowania przez Google", e);
            redirectWithError(ctx, "oauth_failed");
        }
    }

    private User completeLogin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String expectedState = session == null ? null : (String) session.getAttribute(ATTR_STATE);
        String nonce = session == null ? null : (String) session.getAttribute(ATTR_NONCE);

        // state i nonce sa jednorazowe
        if (session != null) {
            session.removeAttribute(ATTR_STATE);
            session.removeAttribute(ATTR_NONCE);
        }

        if (expectedState == null || nonce == null) {
            throw new OAuthException("authorization_request_not_found");
        }

        String state = request.getParameter("state");
        if (state == null || !MessageDigest.isEqual(
                expectedState.getBytes(StandardCharsets.UTF_8),
                state.getBytes(StandardCharsets.UTF_8))) {
            throw new OAuthException("invalid_state_parameter");
        }

        // np. access_denied, gdy uzytkownik anulowal logowanie w Google
        String error = request.getParameter("error");
        if (error != null && !error.isBlank()) {
            throw new OAuthException(error);
        }

        String code = request.getParameter("code");
        if (code == null || code.isBlank()) {
            throw new OAuthException("invalid_request");
        }

        GoogleOAuth.Profile profile = google.fetchProfile(code, redirectUri(request), nonce);

        if (profile.email() == null || profile.email().isBlank() || profile.subject() == null) {
            throw new OAuthException("invalid_id_token");
        }

        // Laczymy konta po emailu tylko wtedy, gdy Google potwierdza ten adres
        if (!profile.emailVerified()) {
            throw new OAuthException("email_not_verified");
        }

        User user = authService.getOrCreateGoogleUser(
                profile.email(), profile.subject(), profile.name(), profile.picture());

        if (user.isBanned()) {
            throw new OAuthException("account_banned");
        }

        return user;
    }

    /* ------------------------------------------------------------------ */

    private void redirectWithError(Ctx ctx, String code) throws IOException {
        ctx.redirect(frontendUrl + "/frontend/pages/login.html?error="
                + URLEncoder.encode(code, StandardCharsets.UTF_8));
    }

    /** Adres powrotu zbudowany z biezacego zadania (jak {baseUrl} w Spring Security). */
    private static String redirectUri(HttpServletRequest request) {
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);

        return scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port) + CALLBACK_PATH;
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
