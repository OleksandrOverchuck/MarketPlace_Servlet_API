package pl.jollycart.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import pl.jollycart.web.Json;

/**
 * Klient Google OpenID Connect (przeplyw "authorization code").
 * Odpowiednik tego, co w Spring Boot robi spring-security-oauth2-client.
 *
 * Uwaga o podpisie ID tokena: token odbieramy BEZPOSREDNIO z endpointu tokenow Google
 * po TLS, uwierzytelniajac sie sekretem klienta. Specyfikacja OpenID Connect Core
 * dopuszcza wtedy pominiecie weryfikacji podpisu. Sprawdzamy natomiast pozostale
 * pola: iss, aud, exp i nonce. Spring Security dodatkowo weryfikuje podpis (JWK).
 */
public final class GoogleOAuth {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuth.class);

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final long CLOCK_SKEW_SECONDS = 60;

    /** Dane z ID tokena potrzebne do zalozenia / znalezienia konta. */
    public record Profile(String email, boolean emailVerified, String subject, String name, String picture) {
    }

    private final String clientId;
    private final String clientSecret;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public GoogleOAuth(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    /** Adres, na ktory przekierowujemy uzytkownika do logowania w Google. */
    public String authorizationUrl(String redirectUri, String state, String nonce) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", clientId);
        params.put("redirect_uri", redirectUri);
        params.put("response_type", "code");
        params.put("scope", "openid profile email");
        params.put("state", state);
        params.put("nonce", nonce);

        return AUTH_URL + "?" + form(params);
    }

    /** Wymienia kod autoryzacyjny na tokeny i zwraca zweryfikowane dane z ID tokena. */
    public Profile fetchProfile(String code, String redirectUri, String expectedNonce) {
        JsonNode tokens = exchangeCode(code, redirectUri);

        String idToken = tokens.path("id_token").asText("");
        if (idToken.isEmpty()) {
            throw new OAuthException("invalid_token_response");
        }

        JsonNode claims = parseClaims(idToken);
        validateClaims(claims, expectedNonce);

        return new Profile(
                text(claims, "email"),
                claims.path("email_verified").asBoolean(false),
                text(claims, "sub"),
                text(claims, "name"),
                text(claims, "picture")
        );
    }

    /* ------------------------------------------------------------------ */

    private JsonNode exchangeCode(String code, String redirectUri) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", "authorization_code");
        params.put("code", code);
        params.put("redirect_uri", redirectUri);
        params.put("client_id", clientId);
        params.put("client_secret", clientSecret);

        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(params)))
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Endpoint tokenow Google zwrocil HTTP {}", response.statusCode());
                throw new OAuthException("invalid_token_response");
            }

            return Json.MAPPER.readTree(response.body());

        } catch (IOException e) {
            log.warn("Blad komunikacji z Google: {}", e.getMessage());
            throw new OAuthException("invalid_token_response");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OAuthException("invalid_token_response");
        }
    }

    private JsonNode parseClaims(String idToken) {
        String[] parts = idToken.split("\\.");
        if (parts.length != 3) {
            throw new OAuthException("invalid_id_token");
        }

        try {
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            return Json.MAPPER.readTree(new String(payload, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | IOException e) {
            throw new OAuthException("invalid_id_token");
        }
    }

    private void validateClaims(JsonNode claims, String expectedNonce) {
        if (!ISSUERS.contains(claims.path("iss").asText(""))) {
            throw new OAuthException("invalid_id_token");
        }

        if (!audienceMatches(claims.path("aud"))) {
            throw new OAuthException("invalid_id_token");
        }

        long expiresAt = claims.path("exp").asLong(0);
        if (expiresAt + CLOCK_SKEW_SECONDS < Instant.now().getEpochSecond()) {
            throw new OAuthException("invalid_id_token");
        }

        if (!expectedNonce.equals(claims.path("nonce").asText(null))) {
            throw new OAuthException("invalid_nonce");
        }
    }

    private boolean audienceMatches(JsonNode audience) {
        if (audience.isArray()) {
            for (JsonNode element : audience) {
                if (clientId.equals(element.asText())) {
                    return true;
                }
            }
            return false;
        }
        return clientId.equals(audience.asText(""));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private static String form(Map<String, String> params) {
        return params.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
