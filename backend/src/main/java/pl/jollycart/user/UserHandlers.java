package pl.jollycart.user;

import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Path;

import jakarta.servlet.http.Part;

import pl.jollycart.file.FileService;
import pl.jollycart.file.ImageFileValidator;
import pl.jollycart.web.Ctx;
import pl.jollycart.web.Router;

/** Endpointy /api/users/* (odpowiednik UserController). */
public final class UserHandlers {

    private static final String AVATAR_URL_PREFIX = "/api/users/avatar/";
    private static final long MAX_AVATAR_SIZE = 5L * 1024 * 1024;

    private final UserService userService;
    private final PasswordService passwordService;
    private final FileService fileService;
    private final ImageFileValidator imageFileValidator;
    private final UserDao userDao;

    public UserHandlers(
            UserService userService,
            PasswordService passwordService,
            FileService fileService,
            ImageFileValidator imageFileValidator,
            UserDao userDao
    ) {
        this.userService = userService;
        this.passwordService = passwordService;
        this.fileService = fileService;
        this.imageFileValidator = imageFileValidator;
        this.userDao = userDao;
    }

    public void routes(Router router) {
        router.get("/api/users/me", this::getCurrentUser);
        router.put("/api/users/me", this::updateCurrentUser);
        router.put("/api/users/me/password", this::changePassword);
        router.post("/api/users/me/avatar", this::uploadAvatar);
        router.delete("/api/users/me/avatar", this::deleteAvatar);
        router.get("/api/users/avatar/{storedFileName}", this::getAvatar);
    }

    private void getCurrentUser(Ctx ctx) throws IOException {
        ctx.json(200, userService.getUserByEmail(ctx.currentUser().email()));
    }

    private void updateCurrentUser(Ctx ctx) throws IOException {
        UpdateUserRequest request = ctx.body(UpdateUserRequest.class);
        ctx.json(200, userService.updateUser(ctx.currentUser().email(), request));
    }

    private void changePassword(Ctx ctx) throws IOException {
        ChangePasswordRequest request = ctx.body(ChangePasswordRequest.class);
        passwordService.changePassword(ctx.currentUser().email(), request);
        ctx.noContent();
    }

    private void uploadAvatar(Ctx ctx) throws IOException {
        Part file = ctx.part("file");
        imageFileValidator.validate(file, MAX_AVATAR_SIZE);

        User user = findCurrentUser(ctx);
        String oldAvatarUrl = user.avatarUrl();

        String storedFileName = fileService.storeFile(file);
        String newAvatarUrl = AVATAR_URL_PREFIX + storedFileName;

        userDao.updateAvatarUrl(user.id(), newAvatarUrl);
        deleteOldLocalAvatar(oldAvatarUrl);

        ctx.json(200, UserResponse.from(user.withAvatarUrl(newAvatarUrl)));
    }

    private void deleteAvatar(Ctx ctx) throws IOException {
        User user = findCurrentUser(ctx);
        String oldAvatarUrl = user.avatarUrl();

        userDao.updateAvatarUrl(user.id(), null);
        deleteOldLocalAvatar(oldAvatarUrl);

        ctx.json(200, UserResponse.from(user.withAvatarUrl(null)));
    }

    private void getAvatar(Ctx ctx) throws IOException {
        Path file = fileService.loadFile(ctx.pathParam("storedFileName"));

        String contentType = URLConnection.guessContentTypeFromName(file.getFileName().toString());
        ctx.sendFile(file, contentType != null ? contentType : "application/octet-stream");
    }

    private User findCurrentUser(Ctx ctx) {
        return userDao.findByEmail(ctx.currentUser().email())
                .orElseThrow(() -> new IllegalArgumentException("Użytkownik nie został znaleziony"));
    }

    /** Usuwa z dysku poprzedni awatar, ale tylko jesli byl zapisany lokalnie (a nie np. adres z Google). */
    private void deleteOldLocalAvatar(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith(AVATAR_URL_PREFIX)) {
            return;
        }

        String storedFileName = avatarUrl.substring(AVATAR_URL_PREFIX.length());
        if (!storedFileName.isBlank()) {
            fileService.deleteFile(storedFileName);
        }
    }
}
