package pl.jollycart.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prosty router: dopasowuje metode HTTP i sciezke (z parametrami {id}) do handlera.
 * Odpowiednik mapowania @GetMapping / @PostMapping w Spring MVC.
 */
public final class Router {

    private record Route(String method, Pattern pattern, List<String> names, Handler handler) {
    }

    public record Match(Handler handler, Map<String, String> pathParams) {
    }

    private final List<Route> routes = new ArrayList<>();

    public void get(String path, Handler handler) {
        add("GET", path, handler);
    }

    public void post(String path, Handler handler) {
        add("POST", path, handler);
    }

    public void put(String path, Handler handler) {
        add("PUT", path, handler);
    }

    public void patch(String path, Handler handler) {
        add("PATCH", path, handler);
    }

    public void delete(String path, Handler handler) {
        add("DELETE", path, handler);
    }

    private void add(String method, String template, Handler handler) {
        List<String> names = new ArrayList<>();
        Matcher variables = Pattern.compile("\\{(\\w+)}").matcher(template);

        StringBuilder regex = new StringBuilder("^");
        int last = 0;

        while (variables.find()) {
            regex.append(Pattern.quote(template.substring(last, variables.start())));
            regex.append("([^/]+)");
            names.add(variables.group(1));
            last = variables.end();
        }

        regex.append(Pattern.quote(template.substring(last))).append("$");
        routes.add(new Route(method, Pattern.compile(regex.toString()), names, handler));
    }

    /** Zwraca dopasowanie albo null, gdy nie ma takiego endpointu. */
    public Match match(String method, String path) {
        for (Route route : routes) {
            if (!route.method().equals(method)) {
                continue;
            }

            Matcher matcher = route.pattern().matcher(path);
            if (matcher.matches()) {
                Map<String, String> params = new LinkedHashMap<>();
                for (int i = 0; i < route.names().size(); i++) {
                    params.put(route.names().get(i), matcher.group(i + 1));
                }
                return new Match(route.handler(), params);
            }
        }
        return null;
    }

    /** Czy sciezka istnieje dla jakiejkolwiek metody (rozroznia 404 od 405). */
    public boolean pathExists(String path) {
        return routes.stream().anyMatch(route -> route.pattern().matcher(path).matches());
    }
}
