package pl.jollycart.web;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import pl.jollycart.user.User;
import pl.jollycart.web.filter.UserStatusFilter;

/** Kontekst jednego zadania: request, response, parametry sciezki i pomocnicze metody. */
public final class Ctx {

    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final Map<String, String> pathParams;

    public Ctx(HttpServletRequest request, HttpServletResponse response, Map<String, String> pathParams) {
        this.request = request;
        this.response = response;
        this.pathParams = pathParams;
    }

    public HttpServletRequest request() {
        return request;
    }

    public HttpServletResponse response() {
        return response;
    }

    public String pathParam(String name) {
        return pathParams.get(name);
    }

    /** Czyta body jako JSON do podanego typu i waliduje je (jak @Valid @RequestBody). */
    public <T> T body(Class<T> type) throws IOException {
        T value;
        try {
            value = Json.MAPPER.readValue(request.getInputStream(), type);
        } catch (IOException e) {
            throw new IllegalArgumentException("Nieprawidłowe dane żądania");
        }

        if (value == null) {
            throw new IllegalArgumentException("Nieprawidłowe dane żądania");
        }

        return RequestValidator.validated(value);
    }

    public void json(int status, Object body) throws IOException {
        Json.write(response, status, body);
    }

    /** Przekierowanie HTTP 302. */
    public void redirect(String url) throws IOException {
        response.sendRedirect(url);
    }

    public void noContent() {
        response.setStatus(204);
    }

    /** Zalogowany uzytkownik (ustawiany przez UserStatusFilter) albo null. */
    public User currentUser() {
        return (User) request.getAttribute(UserStatusFilter.ATTR_USER);
    }
}
