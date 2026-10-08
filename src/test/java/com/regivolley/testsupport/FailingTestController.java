package com.regivolley.testsupport;

import com.regivolley.api.domain.exception.CoachCannotBookOwnSessionException;
import com.regivolley.api.domain.exception.DuplicateBookingException;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.InvalidSessionException;
import com.regivolley.api.domain.exception.JoinRequestNotPossibleException;
import com.regivolley.api.domain.exception.LastAdministratorException;
import com.regivolley.api.domain.exception.MemberAnonymisedException;
import com.regivolley.api.domain.exception.MemberEmailAlreadyUsedException;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.exception.SessionModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.SessionNotFoundException;
import com.regivolley.api.domain.exception.ShortNameAlreadyTakenException;
import com.regivolley.api.domain.exception.ShortNameNotFoundException;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.model.valueobject.ShortName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.UUID;

/**
 * Test-only routes that raise each family of exception. It lives outside {@code com.regivolley.api} on purpose, so the
 * application's component scan never picks it up: a test registers it with {@code @Import}, and the route-inventory test
 * never sees it.
 */
@Controller
@RequestMapping("/api/v1/test")
@ResponseBody
public class FailingTestController {

    public record Probe(@NotBlank @Email String email, @Size(max = 5) String note) {
    }

    public record IdProbe(UUID id) {
    }

    /** Never leaves the test: its message holds what must not reach a client or a log. */
    public static final String SECRET_MESSAGE = "could not execute SQL for ana.silva@example.com password=hunter2";

    @GetMapping("/fail/{kind}")
    public String fail(@PathVariable String kind) {
        switch (kind) {
            case "not-allowed" -> throw new NotAllowedException("cancel this booking");
            case "member-not-found" -> throw new MemberNotFoundException(MemberId.generate());
            case "session-not-found" -> throw new SessionNotFoundException(SessionId.generate());
            case "short-name-not-found" -> throw new ShortNameNotFoundException(ShortName.of("my-club"));
            case "join-request-not-possible" -> throw new JoinRequestNotPossibleException();
            case "short-name-taken" -> throw new ShortNameAlreadyTakenException(ShortName.of("my-club"));
            case "email-used" -> throw new MemberEmailAlreadyUsedException();
            case "last-admin" -> throw new LastAdministratorException();
            case "duplicate-booking" -> throw new DuplicateBookingException();
            case "concurrent" -> throw new SessionModifiedConcurrentlyException(SessionId.generate());
            case "invalid-field" -> throw new InvalidFieldException("email", "The email address is not valid");
            case "business-rule" -> throw new CoachCannotBookOwnSessionException();
            case "anonymised" -> throw new MemberAnonymisedException();
            case "invariant" -> throw new InvalidSessionException(SECRET_MESSAGE);
            case "runtime" -> throw new IllegalStateException(SECRET_MESSAGE);
            case "data-integrity" -> throw new DataIntegrityViolationException(SECRET_MESSAGE);
            case "access-denied" -> throw new AccessDeniedException(SECRET_MESSAGE);
            case "authentication" -> throw new BadCredentialsException(SECRET_MESSAGE);
            default -> throw new UnsupportedOperationException(kind);
        }
    }

    @PostMapping("/validated")
    public String validated(@Valid @RequestBody Probe probe) {
        return "ok";
    }

    @PostMapping("/typed")
    public String typed(@RequestBody IdProbe probe) {
        return "ok";
    }

    /** Built-in method validation of a path variable: raises HandlerMethodValidationException. */
    @GetMapping("/named/{name}")
    public String named(@PathVariable @Size(max = 3) String name) {
        return name;
    }

    /** The template names {present} but the method asks for {absent}: raises MissingPathVariableException (a 500, not the client's fault). */
    @GetMapping("/missing-path/{present}")
    public String missingPath(@PathVariable("absent") String absent) {
        return absent;
    }

    @GetMapping("/by-id/{id}")
    public String byId(@PathVariable UUID id) {
        return id.toString();
    }
}
