package pl.jollycart.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import pl.jollycart.user.User;

/**
 * Zapamietywanie zalogowanego uzytkownika w sesji HTTP (cookie JSESSIONID).
 * W sesji trzymamy tylko e-mail - tak jak Spring Security (authentication.getName()).
 */
public final class SessionAuth {

    private static final String ATTR_EMAIL = "jollycart.userEmail";

    private SessionAuth() {
    }

    public static void login(HttpServletRequest request, User user) {
        // zmiana identyfikatora istniejacej sesji - ochrona przed session fixation
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        request.getSession(true).setAttribute(ATTR_EMAIL, user.email());
    }

    public static void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    /** E-mail zalogowanego uzytkownika albo null, gdy nikt nie jest zalogowany. */
    public static String currentEmail(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return (String) session.getAttribute(ATTR_EMAIL);
    }
}
