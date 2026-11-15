package pl.jollycart.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

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

    /** Parametr sciezki jako liczba (np. id oferty); zla wartosc to blad 400. */
    public long pathLong(String name) {
        try {
            return Long.parseLong(pathParams.get(name));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Nieprawidłowy identyfikator");
        }
    }

    /** Parametr zapytania (?name=...) albo null, gdy go nie ma lub jest pusty. */
    public String query(String name) {
        String value = request.getParameter(name);
        return (value == null || value.isBlank()) ? null : value;
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

    /** Czesc zadania multipart/form-data o podanej nazwie (jak @RequestParam MultipartFile). */
    public Part part(String name) throws IOException {
        Part part;
        try {
            part = request.getPart(name);
        } catch (ServletException e) {
            throw new IllegalArgumentException("Żądanie musi być typu multipart/form-data");
        } catch (IllegalStateException e) {
            // przekroczony limit rozmiaru ustawiony w MultipartConfigElement
            throw new PayloadTooLargeException("Plik jest zbyt duży");
        }

        if (part == null) {
            throw new IllegalArgumentException("Brak pliku w żądaniu");
        }
        return part;
    }

    /** Wysyla plik z dysku jako odpowiedz (inline), z podanym typem zawartosci. */
    public void sendFile(Path file, String contentType) throws IOException {
        sendFile(file, contentType, file.getFileName().toString());
    }

    /** Jak wyzej, ale z nazwa pliku pokazywana przegladarce (oczyszczona z cudzyslowow i znakow sterujacych). */
    public void sendFile(Path file, String contentType, String displayName) throws IOException {
        String safeName = displayName.replaceAll("[\"\\\\\\p{Cntrl}]", "_");

        response.setStatus(200);
        response.setContentType(contentType);
        response.setContentLengthLong(Files.size(file));
        response.setHeader("Content-Disposition", "inline; filename=\"" + safeName + "\"");
        Files.copy(file, response.getOutputStream());
    }

    /** Wysyla plik z dysku z dowolnymi naglowkami odpowiedzi (np. Content-Disposition, Cache-Control). */
    public void sendFile(Path file, String contentType, Map<String, String> headers) throws IOException {
        response.setStatus(200);
        response.setContentType(contentType);
        response.setContentLengthLong(Files.size(file));
        headers.forEach(response::setHeader);
        Files.copy(file, response.getOutputStream());
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
