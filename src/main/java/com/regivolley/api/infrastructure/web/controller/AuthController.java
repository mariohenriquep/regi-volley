package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.command.ActivateAccountCommand;
import com.regivolley.api.application.command.LoginCommand;
import com.regivolley.api.application.command.LogoutAllCommand;
import com.regivolley.api.application.command.LogoutCommand;
import com.regivolley.api.application.command.RefreshSessionCommand;
import com.regivolley.api.application.command.RequestPasswordResetCommand;
import com.regivolley.api.application.command.ResetPasswordCommand;
import com.regivolley.api.application.result.SessionTokens;
import com.regivolley.api.application.usecase.ActivateAccountUseCase;
import com.regivolley.api.application.usecase.LoginUseCase;
import com.regivolley.api.application.usecase.LogoutAllUseCase;
import com.regivolley.api.application.usecase.LogoutUseCase;
import com.regivolley.api.application.usecase.RefreshSessionUseCase;
import com.regivolley.api.application.usecase.RequestPasswordResetUseCase;
import com.regivolley.api.application.usecase.ResetPasswordUseCase;
import com.regivolley.api.infrastructure.security.AuthenticatedActor;
import com.regivolley.api.infrastructure.security.CurrentActor;
import com.regivolley.api.infrastructure.web.dto.AccessTokenResponse;
import com.regivolley.api.infrastructure.web.dto.AcknowledgementResponse;
import com.regivolley.api.infrastructure.web.dto.ActivateAccountRequest;
import com.regivolley.api.infrastructure.web.dto.ForgotPasswordRequest;
import com.regivolley.api.infrastructure.web.dto.LoginRequest;
import com.regivolley.api.infrastructure.web.dto.ResetPasswordRequest;
import com.regivolley.api.infrastructure.web.mapper.AuthWebMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

/**
 * The credential endpoints (threat model D-7..D-11). Everything here is translation between HTTP and the use cases: the
 * services decide, the exception advice turns their refusals into the uniform answers, and the security filters (rate limits,
 * Origin and content-type on the two cookie routes) run before any of this. The access token goes in the body, the refresh
 * token only in an HttpOnly cookie ({@link RefreshCookie}).
 *
 * <p>Public (no access token): login, refresh, logout, activate, password-reset-requests, password-resets. Authenticated:
 * logout-all.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginUseCase login;
    private final RefreshSessionUseCase refreshSession;
    private final LogoutUseCase logout;
    private final LogoutAllUseCase logoutAll;
    private final ActivateAccountUseCase activateAccount;
    private final RequestPasswordResetUseCase requestPasswordReset;
    private final ResetPasswordUseCase resetPassword;
    private final Clock clock;

    public AuthController(LoginUseCase login, RefreshSessionUseCase refreshSession, LogoutUseCase logout, LogoutAllUseCase logoutAll,
                          ActivateAccountUseCase activateAccount, RequestPasswordResetUseCase requestPasswordReset,
                          ResetPasswordUseCase resetPassword, Clock clock) {
        this.login = login;
        this.refreshSession = refreshSession;
        this.logout = logout;
        this.logoutAll = logoutAll;
        this.activateAccount = activateAccount;
        this.requestPasswordReset = requestPasswordReset;
        this.resetPassword = resetPassword;
        this.clock = clock;
    }

    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return withSession(login.execute(new LoginCommand(body.email(), body.password(), request.getRemoteAddr())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(@CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        return withSession(refreshSession.execute(new RefreshSessionCommand(refreshToken)));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken, HttpServletResponse response) {
        logout.execute(new LogoutCommand(refreshToken));
        response.addHeader(HttpHeaders.SET_COOKIE, RefreshCookie.clear());
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@CurrentActor AuthenticatedActor caller, HttpServletResponse response) {
        logoutAll.execute(new LogoutAllCommand(caller.userId()));
        response.addHeader(HttpHeaders.SET_COOKIE, RefreshCookie.clear());
    }

    @PostMapping("/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activate(@Valid @RequestBody ActivateAccountRequest body) {
        activateAccount.execute(new ActivateAccountCommand(body.token(), body.password()));
    }

    @PostMapping("/password-reset-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AcknowledgementResponse requestPasswordReset(@Valid @RequestBody ForgotPasswordRequest body) {
        requestPasswordReset.execute(new RequestPasswordResetCommand(body.email()));
        return AcknowledgementResponse.received();
    }

    @PostMapping("/password-resets")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
        resetPassword.execute(new ResetPasswordCommand(body.token(), body.password()));
    }

    private ResponseEntity<AccessTokenResponse> withSession(SessionTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, RefreshCookie.issue(tokens.refreshToken(), tokens.refreshExpiresAt(), clock))
                .body(AuthWebMapper.toResponse(tokens));
    }
}
