package pl.jollycart.auth;

/** Blad logowania przez Google. Kod trafia do frontendu jako login.html?error=<kod>. */
public class OAuthException extends RuntimeException {

    private final String code;

    public OAuthException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
