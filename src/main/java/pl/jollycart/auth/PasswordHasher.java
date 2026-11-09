package pl.jollycart.auth;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Haszowanie hasel i kodow weryfikacyjnych algorytmem BCrypt (koszt 10).
 * Hasze sa zgodne z BCryptPasswordEncoder ze Spring Security ($2a$10$...).
 */
public final class PasswordHasher {

    public String encode(String rawValue) {
        return BCrypt.hashpw(rawValue, BCrypt.gensalt());
    }

    public boolean matches(String rawValue, String hash) {
        try {
            return BCrypt.checkpw(rawValue, hash);
        } catch (IllegalArgumentException e) {
            // niepoprawny format hasha
            return false;
        }
    }
}
