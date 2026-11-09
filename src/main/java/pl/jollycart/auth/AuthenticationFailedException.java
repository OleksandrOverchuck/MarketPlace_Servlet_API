package pl.jollycart.auth;

/** Zle dane logowania - mapowane na HTTP 401 (jak BadCredentialsException w Spring Security). */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException() {
        super("Nieprawidłowy email lub hasło");
    }
}
