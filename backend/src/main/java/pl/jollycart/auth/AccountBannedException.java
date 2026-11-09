package pl.jollycart.auth;

public class AccountBannedException extends RuntimeException {

    private final String reason;

    public AccountBannedException(String reason) {
        super("Twoje konto zostało zablokowane");
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
