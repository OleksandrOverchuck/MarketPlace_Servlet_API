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
}
