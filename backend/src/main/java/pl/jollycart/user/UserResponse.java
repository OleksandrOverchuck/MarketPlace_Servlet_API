package pl.jollycart.user;

/** JSON zwracany frontendowi - identyczny z UserResponse w wersji Spring Boot. */
public record UserResponse(
        Long id,
        String nickname,
        String email,
        String avatarUrl,
        String phone,
        String location,
        AuthProvider authProvider,
        Role role,
        boolean passwordChangeRequired
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.id(),
                user.nickname(),
                user.email(),
                user.avatarUrl(),
                user.phone(),
                user.location(),
                user.authProvider(),
                user.role(),
                user.passwordChangeRequired()
        );
    }
}
