package pl.jollycart.user;

import pl.jollycart.db.Db;

public final class UserService {

    private final UserDao userDao;

    public UserService(UserDao userDao) {
        this.userDao = userDao;
    }

    public UserResponse getUserByEmail(String email) {
        User user = userDao.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Użytkownik nie został znaleziony"));

        return UserResponse.from(user);
    }

    public UserResponse updateUser(String currentEmail, UpdateUserRequest request) {
        return Db.inTransaction(() -> {
            User user = userDao.findByEmail(currentEmail)
                    .orElseThrow(() -> new IllegalStateException("Użytkownik nie został znaleziony"));

            boolean nicknameChanged = !user.nickname().equals(request.nickname());

            if (nicknameChanged && userDao.existsByNicknameAndIdNot(request.nickname(), user.id())) {
                throw new IllegalArgumentException("Nickname jest już zajęty");
            }

            String phone = normalize(request.phone());
            String location = normalize(request.location());

            userDao.updateProfile(user.id(), request.nickname(), request.avatarUrl(), phone, location);

            return new UserResponse(
                    user.id(),
                    request.nickname(),
                    user.email(),
                    request.avatarUrl(),
                    phone,
                    location,
                    user.authProvider(),
                    user.role(),
                    user.passwordChangeRequired()
            );
        });
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
