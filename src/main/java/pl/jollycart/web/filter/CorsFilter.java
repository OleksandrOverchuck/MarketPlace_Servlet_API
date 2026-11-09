package pl.jollycart.web.filter;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * CORS: frontend dziala pod innym adresem (np. localhost:5501) niz API (localhost:8080),
 * a przegladarka wysyla ciasteczko sesji (credentials: "include").
 * Odpowiednik CorsConfigurationSource z Spring Security.
 */
public final class CorsFilter implements Filter {

    private static final Set<String> ALLOWED_METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    private final Set<String> allowedOrigins;

    public CorsFilter(Set<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String origin = request.getHeader("Origin");

        if (origin == null || isSameOrigin(request, origin)) {
            chain.doFilter(request, response);
            return;
        }

        String requestedMethod = request.getHeader("Access-Control-Request-Method");
        boolean preflight = "OPTIONS".equalsIgnoreCase(request.getMethod()) && requestedMethod != null;

        if (!allowedOrigins.contains(origin)
                || (preflight && !ALLOWED_METHODS.contains(requestedMethod.toUpperCase()))) {
            response.setStatus(403);
            response.setContentType("text/plain");
            response.getWriter().write("Invalid CORS request");
            return;
        }

        response.addHeader("Vary", "Origin");
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Access-Control-Allow-Credentials", "true");

        if (preflight) {
            response.addHeader("Vary", "Access-Control-Request-Method");
            response.addHeader("Vary", "Access-Control-Request-Headers");
            response.setHeader("Access-Control-Allow-Methods", String.join(",", ALLOWED_METHODS));

            String requestedHeaders = request.getHeader("Access-Control-Request-Headers");
            if (requestedHeaders != null) {
                response.setHeader("Access-Control-Allow-Headers", requestedHeaders);
            }

            response.setHeader("Access-Control-Max-Age", "1800");
            response.setStatus(200);
            return;
        }

        chain.doFilter(request, response);
    }

    private static boolean isSameOrigin(HttpServletRequest request, String origin) {
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);

        String self = scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
        return self.equalsIgnoreCase(origin);
    }
}
