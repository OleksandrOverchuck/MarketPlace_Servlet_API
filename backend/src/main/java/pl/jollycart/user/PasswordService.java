package pl.jollycart.user;

import pl.jollycart.auth.PasswordHasher;
import pl.jollycart.db.Db;

public final class PasswordService {

    private final UserDao userDao;
    private final PasswordHasher passwordHasher;

    public PasswordService(UserDao userDao, PasswordHasher passwordHasher) {
        this.userDao = userDao;
        this.passwordHasher = passwordHasher;
    }

    public void changePassword(String email, ChangePasswordRequest request) {
        Db.inTransaction(() -> {
            User user = userDao.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("Użytkownik nie został znaleziony"));

            if (user.authProvider() != AuthProvider.LOCAL || user.passwordHash() == null) {
                throw new IllegalArgumentException("To konto nie używa hasła (logowanie przez Google)");
            }

            if (!passwordHasher.matches(request.currentPassword(), user.passwordHash())) {
                throw new IllegalArgumentException("Obecne hasło jest nieprawidłowe");
            }

            if (!request.newPassword().equals(request.confirmPassword())) {
                throw new IllegalArgumentException("Hasła nie są zgodne");
            }

            if (request.newPassword().equals(request.currentPassword())) {
                throw new IllegalArgumentException("Nowe hasło musi różnić się od obecnego");
            }

            userDao.updatePassword(user.id(), passwordHasher.encode(request.newPassword()));
        });
    }
}
