package pl.jollycart.auth;

import java.io.IOException;

import pl.jollycart.auth.dto.LoginRequest;
import pl.jollycart.auth.dto.RegisterRequest;
import pl.jollycart.auth.dto.ResendCodeRequest;
import pl.jollycart.auth.dto.VerifyEmailRequest;
import pl.jollycart.user.User;
import pl.jollycart.user.UserResponse;
import pl.jollycart.web.Ctx;
import pl.jollycart.web.Router;

/** Endpointy /api/auth/* (odpowiednik AuthController). */
public final class AuthHandlers {

    private final AuthService authService;
    private final RegistrationService registrationService;

    public AuthHandlers(AuthService authService, RegistrationService registrationService) {
        this.authService = authService;
        this.registrationService = registrationService;
    }

    public void routes(Router router) {
        router.post("/api/auth/register", this::register);
        router.post("/api/auth/verify-email", this::verifyEmail);
        router.post("/api/auth/resend-code", this::resendCode);
        router.post("/api/auth/login", this::login);
        router.get("/api/auth/me", this::me);
        router.post("/api/auth/logout", this::logout);
    }

    /** Krok 1 rejestracji: NIE tworzy konta, tylko wysyła kod na email. */
    private void register(Ctx ctx) throws IOException {
        RegisterRequest request = ctx.body(RegisterRequest.class);
        ctx.json(202, registrationService.startRegistration(request));
    }

    /** Krok 2 rejestracji: sprawdza kod i dopiero wtedy tworzy konto. */
    private void verifyEmail(Ctx ctx) throws IOException {
        VerifyEmailRequest request = ctx.body(VerifyEmailRequest.class);
        ctx.json(201, registrationService.verify(request.email(), request.code()));
    }

    private void resendCode(Ctx ctx) throws IOException {
        ResendCodeRequest request = ctx.body(ResendCodeRequest.class);
        ctx.json(200, registrationService.resendCode(request.email()));
    }

    private void login(Ctx ctx) throws IOException {
        LoginRequest request = ctx.body(LoginRequest.class);

        User user = authService.authenticate(request.email(), request.password());
        SessionAuth.login(ctx.request(), user);

        ctx.json(200, UserResponse.from(user));
    }

    private void me(Ctx ctx) throws IOException {
        ctx.json(200, UserResponse.from(ctx.currentUser()));
    }

    private void logout(Ctx ctx) {
        SessionAuth.logout(ctx.request());
        ctx.noContent();
    }
}
