package pl.jollycart.web;

/** Blad z wlasnym kodem HTTP i komunikatem (odpowiednik ResponseStatusException ze Springa). */
public class StatusException extends RuntimeException {

    private final int status;

    public StatusException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static StatusException forbidden(String message) {
        return new StatusException(403, message);
    }

    public static StatusException notFound(String message) {
        return new StatusException(404, message);
    }

    public static StatusException conflict(String message) {
        return new StatusException(409, message);
    }
}
