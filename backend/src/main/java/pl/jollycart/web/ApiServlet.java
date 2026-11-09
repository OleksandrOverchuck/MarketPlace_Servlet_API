package pl.jollycart.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Front controller: jeden servlet obslugujacy cale API.
 * Odpowiednik DispatcherServlet - wybiera handler z routera i obsluguje bledy.
 */
public final class ApiServlet extends HttpServlet {

    private final transient Router router;

    public ApiServlet(Router router) {
        this.router = router;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = WebPaths.of(request);
        Router.Match match = router.match(request.getMethod(), path);

        try {
            if (match == null) {
                int status = router.pathExists(path) ? 405 : 404;
                Json.write(response, status, java.util.Map.of(
                        "message", status == 405 ? "Metoda nie jest obsługiwana" : "Nie znaleziono"));
                return;
            }

            match.handler().handle(new Ctx(request, response, match.pathParams()));

        } catch (Exception e) {
            ApiErrors.handle(e, response);
        }
    }
}
