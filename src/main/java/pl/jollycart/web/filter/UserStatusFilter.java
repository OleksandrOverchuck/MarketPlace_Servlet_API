package pl.jollycart.web.filter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import pl.jollycart.auth.SessionAuth;
import pl.jollycart.user.User;
import pl.jollycart.user.UserDao;
import pl.jollycart.web.Json;
import pl.jollycart.web.WebPaths;

/**
 * Przy KAZDYM zadaniu wczytuje zalogowanego uzytkownika z sesji i sprawdza jego status w bazie.
 * Dzieki temu ban, usuniecie konta albo zmiana roli dzialaja od razu na aktywnej sesji.
 * Ustawia atrybut zadania ATTR_USER, z ktorego korzystaja kolejne filtry i handlery.
 */
public final class UserStatusFilter implements Filter {

    public static final String ATTR_USER = "jollycart.currentUser";

    private final UserDao userDao;

    public UserStatusFilter(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String email = SessionAuth.currentEmail(request);

        if (email == null) {
            chain.doFilter(request, response);
            return;
        }

        Optional<User> found = userDao.findByEmail(email);

        // konto usuniete z bazy
        if (found.isEmpty()) {
            SessionAuth.logout(request);
            Json.write(response, 401, Map.of("message", "Konto nie istnieje"));
            return;
        }

        User user = found.get();

        // konto zablokowane - przerywamy sesje od razu
        if (user.isBanned()) {
            SessionAuth.logout(request);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Twoje konto zostało zablokowane");
            body.put("banned", true);
            body.put("reason", user.banReason() == null ? "" : user.banReason());
            Json.write(response, 403, body);
            return;
        }

        // administrator z tymczasowym haslem musi je zmienic, zanim uzyje panelu
        if (user.passwordChangeRequired() && isStaffPath(WebPaths.of(request))) {
            Json.write(response, 403, Map.of(
                    "message", "Najpierw zmień hasło tymczasowe",
                    "passwordChangeRequired", true));
            return;
        }

        request.setAttribute(ATTR_USER, user);
        chain.doFilter(request, response);
    }

    private static boolean isStaffPath(String path) {
        return path.startsWith("/api/admin") || path.startsWith("/api/moderation");
    }
}
