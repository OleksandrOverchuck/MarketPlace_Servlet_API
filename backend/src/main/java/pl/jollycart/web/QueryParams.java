package pl.jollycart.web;

/** Odczyt i konwersja parametrow zapytania (?page=1&status=ACTIVE); zla wartosc to blad 400. */
public final class QueryParams {

    private QueryParams() {
    }

    public static int intParam(Ctx ctx, String name, int defaultValue) {
        String value = ctx.query(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw invalid(name);
        }
    }

    public static Long longParam(Ctx ctx, String name) {
        String value = ctx.query(name);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw invalid(name);
        }
    }

    public static <E extends Enum<E>> E enumParam(Ctx ctx, String name, Class<E> type) {
        String value = ctx.query(name);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException e) {
            throw invalid(name);
        }
    }

    private static IllegalArgumentException invalid(String name) {
        return new IllegalArgumentException("Nieprawidłowa wartość parametru " + name);
    }
}
