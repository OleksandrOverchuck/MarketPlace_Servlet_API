package pl.jollycart.web.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import pl.jollycart.user.Role;
import pl.jollycart.user.User;
import pl.jollycart.web.Json;
import pl.jollycart.web.WebPaths;

/**
 * Reguly dostepu - odpowiednik authorizeHttpRequests(...) z SecurityConfig.
 * Reguly sprawdzane sa po kolei, pierwsza pasujaca decyduje.
 * Brak pasujacej reguly = wymagane zalogowanie.
 */
public final class AuthorizationFilter implements Filter {

    /** method == null oznacza dowolna metode; roles == null i !permitAll oznacza "zalogowany". */
    private record Rule(String method, Pattern pattern, boolean permitAll, Set<Role> roles) {
    }

    private static final List<Rule> RULES = new ArrayList<>();

    static {
        roles("/api/admin/**", Role.ADMIN);
        roles("/api/moderation/**", Role.MODERATOR, Role.ADMIN);

        authenticated("GET", "/api/offers/me");

        permit("GET", "/api/offers", "/api/offers/**", "/api/categories");
        permit("GET", "/api/users/*/profile");
        permit("POST", "/api/payments/webhook");
        permit("POST", "/api/auth/register", "/api/auth/verify-email",
                "/api/auth/resend-code", "/api/auth/login");
        permit(null, "/oauth2/**", "/login/oauth2/**");
    }

    private static void permit(String method, String... patterns) {
        for (String pattern : patterns) {
            RULES.add(new Rule(method, antToRegex(pattern), true, null));
        }
    }

    private static void authenticated(String method, String... patterns) {
        for (String pattern : patterns) {
            RULES.add(new Rule(method, antToRegex(pattern), false, null));
        }
    }

    private static void roles(String pattern, Role... roles) {
        RULES.add(new Rule(null, antToRegex(pattern), false, Set.of(roles)));
    }

    /** Wzorzec w stylu Ant: * = jeden segment, ** = dowolna liczba segmentow. */
    private static Pattern antToRegex(String ant) {
        StringBuilder regex = new StringBuilder();

        for (int i = 0; i < ant.length(); i++) {
            char c = ant.charAt(i);

            if (c == '*') {
                if (i + 1 < ant.length() && ant.charAt(i + 1) == '*') {
                    regex.append(".*");
                    i++;
                } else {
                    regex.append("[^/]*");
                }
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }

        return Pattern.compile(regex.toString());
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String method = request.getMethod();
        String path = WebPaths.of(request);
        User user = (User) request.getAttribute(UserStatusFilter.ATTR_USER);

        Rule rule = findRule(method, path);

        if (rule != null && rule.permitAll()) {
            chain.doFilter(request, response);
            return;
        }

        if (user == null) {
            response.setStatus(401);
            return;
        }

        if (rule != null && rule.roles() != null && !rule.roles().contains(user.role())) {
            Json.write(response, 403, Map.of("message", "Brak uprawnień"));
            return;
        }

        chain.doFilter(request, response);
    }

    private static Rule findRule(String method, String path) {
        for (Rule rule : RULES) {
            boolean methodMatches = rule.method() == null || rule.method().equals(method);

            if (methodMatches && rule.pattern().matcher(path).matches()) {
                return rule;
            }
        }
        return null;
    }
}
