package pl.jollycart.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletResponse;

import pl.jollycart.auth.AccountBannedException;
import pl.jollycart.auth.AuthenticationFailedException;
import pl.jollycart.auth.EmailSendException;

/** Zamiana wyjatkow na odpowiedzi HTTP (odpowiednik @ExceptionHandler / @ControllerAdvice). */
public final class ApiErrors {

    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    private ApiErrors() {
    }

    public static void handle(Exception exception, HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            log.error("Blad po rozpoczeciu odpowiedzi", exception);
            return;
        }

        if (exception instanceof RequestValidationException validation) {
            Json.write(response, 400, validation.getErrors());

        } else if (exception instanceof IllegalArgumentException) {
            Json.write(response, 400, Map.of("message", exception.getMessage()));

        } else if (exception instanceof AuthenticationFailedException) {
            response.setStatus(401);

        } else if (exception instanceof AccountBannedException banned) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", banned.getMessage());
            body.put("banned", true);
            body.put("reason", banned.getReason());
            Json.write(response, 403, body);

        } else if (exception instanceof EmailSendException) {
            Json.write(response, 503, Map.of("message", exception.getMessage()));

        } else {
            log.error("Nieobsluzony blad", exception);
            Json.write(response, 500, Map.of("message", "Wystąpił błąd serwera"));
        }
    }
}
