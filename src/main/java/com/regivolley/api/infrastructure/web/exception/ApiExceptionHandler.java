package com.regivolley.api.infrastructure.web.exception;

import com.regivolley.api.domain.exception.AggregateModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.AssociationNotFoundException;
import com.regivolley.api.domain.exception.BookingNotFoundException;
import com.regivolley.api.domain.exception.BusinessRuleException;
import com.regivolley.api.domain.exception.DuplicateBookingException;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.JoinRequestNotFoundException;
import com.regivolley.api.domain.exception.JoinRequestNotPossibleException;
import com.regivolley.api.domain.exception.LastAdministratorException;
import com.regivolley.api.domain.exception.LevelNotFoundException;
import com.regivolley.api.domain.exception.MemberEmailAlreadyUsedException;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.exception.PaymentNotFoundException;
import com.regivolley.api.domain.exception.PlanNotFoundException;
import com.regivolley.api.domain.exception.SessionNotFoundException;
import com.regivolley.api.domain.exception.ShortNameAlreadyTakenException;
import com.regivolley.api.domain.exception.ShortNameNotFoundException;
import com.regivolley.api.domain.exception.SubscriptionNotFoundException;
import com.regivolley.api.domain.exception.TrainingGroupNotFoundException;
import com.regivolley.api.domain.exception.VenueNotFoundException;
import com.regivolley.api.application.exception.InvalidCredentialsException;
import com.regivolley.api.application.exception.InvalidLinkException;
import com.regivolley.api.application.exception.InvalidRefreshTokenException;
import com.regivolley.api.application.exception.RateLimitExceededException;
import com.regivolley.api.application.exception.ServiceBusyException;
import com.regivolley.api.infrastructure.security.RequestIds;
import com.regivolley.api.infrastructure.web.dto.AcknowledgementResponse;
import com.regivolley.api.infrastructure.web.dto.ApiError;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * The single place where exceptions become HTTP (threat model section 7, D-13). One body shape ({@link ApiError}), codes chosen
 * by this explicit mapping and never derived from class names, and nothing that could carry personal data:
 *
 * <ul>
 *   <li>403 {@link NotAllowedException}; 404 every {@code *NotFoundException}, which is also what another tenant's id
 *       gets (D-12); 202 {@link JoinRequestNotPossibleException} (D-14); 409 the conflicts and
 *       {@link AggregateModifiedConcurrentlyException}; 422 every other {@link BusinessRuleException} with its own
 *       English message;</li>
 *   <li>401 a failed login or refresh, 400 an invalid emailed link, 429 a rate limit with {@code Retry-After}: one answer per
 *       failure, whatever the reason (D-10);</li>
 *   <li>400 malformed, invalid or unknown-property input (names of fields, never rejected values) and the other
 *       Spring MVC errors through {@link ResponseEntityExceptionHandler} (404, 405, 406, 415, ...);</li>
 *   <li>500 everything else: a generic body, and a log line with the exception class and the place it was thrown, never its
 *       message (it may quote data).</li>
 * </ul>
 *
 * Spring picks the handler whose exception type is nearest, so {@code NotAllowedException} wins over the generic
 * {@code BusinessRuleException} regardless of declaration order. Errors raised in the filter chain never reach this
 * advice: the entry point and access-denied handler of the security layer write the same shape.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String CONCURRENT_MODIFICATION_MESSAGE = "The data was changed by someone else in the meantime. Reload and try again";

    // ---- business rules ----------------------------------------------------------------------------------------

    @ExceptionHandler(NotAllowedException.class)
    public ResponseEntity<ApiError> handleNotAllowed(NotAllowedException ex) {
        return json(HttpStatus.FORBIDDEN, new ApiError(ApiError.NOT_ALLOWED, ex.getMessage(), requestId()));
    }

    @ExceptionHandler({
            AssociationNotFoundException.class,
            BookingNotFoundException.class,
            JoinRequestNotFoundException.class,
            LevelNotFoundException.class,
            MemberNotFoundException.class,
            PaymentNotFoundException.class,
            PlanNotFoundException.class,
            SessionNotFoundException.class,
            ShortNameNotFoundException.class,
            SubscriptionNotFoundException.class,
            TrainingGroupNotFoundException.class,
            VenueNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex) {
        // Same answer for "does not exist" and "belongs to another association", and no id echoed back.
        return json(HttpStatus.NOT_FOUND, ApiError.ofStatus(404, requestId()));
    }

    @ExceptionHandler(JoinRequestNotPossibleException.class)
    public ResponseEntity<AcknowledgementResponse> handleJoinRequestNotPossible(JoinRequestNotPossibleException ex) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                .body(AcknowledgementResponse.received());
    }

    @ExceptionHandler({
            ShortNameAlreadyTakenException.class,
            MemberEmailAlreadyUsedException.class,
            LastAdministratorException.class,
            DuplicateBookingException.class})
    public ResponseEntity<ApiError> handleConflict(BusinessRuleException ex) {
        return json(HttpStatus.CONFLICT, new ApiError(ApiError.CONFLICT, ex.getMessage(), requestId()));
    }

    @ExceptionHandler(AggregateModifiedConcurrentlyException.class)
    public ResponseEntity<ApiError> handleConcurrentModification(AggregateModifiedConcurrentlyException ex) {
        LOG.info("Concurrent modification: aggregate={} id={} requestId={}", ex.aggregate(), ex.aggregateId(), requestId());
        return json(HttpStatus.CONFLICT, new ApiError(ApiError.CONFLICT, CONCURRENT_MODIFICATION_MESSAGE, requestId()));
    }

    @ExceptionHandler(InvalidFieldException.class)
    public ResponseEntity<ApiError> handleInvalidField(InvalidFieldException ex) {
        return json(HttpStatus.UNPROCESSABLE_CONTENT,
                new ApiError(ApiError.INVALID_FIELD, ex.getMessage(), requestId(), List.of(ex.field())));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex) {
        return json(HttpStatus.UNPROCESSABLE_CONTENT, new ApiError(ApiError.BUSINESS_RULE_VIOLATION, ex.getMessage(), requestId()));
    }

    // ---- security exceptions thrown from inside a handler -------------------------------------------------------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return json(HttpStatus.FORBIDDEN, ApiError.forbidden(requestId()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(ApiError.unauthenticated(requestId()));
    }

    // ---- credentials: one answer per failure, whatever the reason (threat model D-10) -----------------------------

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
        return json(HttpStatus.UNAUTHORIZED, ApiError.invalidCredentials(requestId()));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        return json(HttpStatus.UNAUTHORIZED, ApiError.unauthenticated(requestId()));
    }

    @ExceptionHandler(InvalidLinkException.class)
    public ResponseEntity<ApiError> handleInvalidLink(InvalidLinkException ex) {
        return json(HttpStatus.BAD_REQUEST, ApiError.invalidLink(requestId()));
    }

    @ExceptionHandler(ServiceBusyException.class)
    public ResponseEntity<ApiError> handleServiceBusy(ServiceBusyException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
                .body(ApiError.serviceBusy(requestId()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimited(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
                .body(ApiError.tooManyRequests(requestId()));
    }

    // ---- bad input -----------------------------------------------------------------------------------------------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<String> fields = ex.getBindingResult().getFieldErrors().stream().map(error -> error.getField()).distinct().sorted().toList();
        LOG.info("Invalid request: exception={} fields={} requestId={}", ex.getClass().getSimpleName(), fields, requestId());
        return respond(headers, status, new ApiError(ApiError.INVALID_REQUEST, "Invalid request", requestId(), fields));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex, HttpHeaders headers,
                                                                            HttpStatusCode status, WebRequest request) {
        LOG.info("Invalid request: exception={} requestId={}", ex.getClass().getSimpleName(), requestId());
        return respond(headers, status, ApiError.ofStatus(400, requestId()));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        if (isCausedBy(ex, MaxUploadSizeExceededException.class)) {
            // The request-size filter cut the body short while a message converter was reading it.
            LOG.info("Request body too large: requestId={}", requestId());
            return respond(headers, HttpStatus.PAYLOAD_TOO_LARGE, ApiError.ofStatus(413, requestId()));
        }
        // The cause of a parse error can quote the offending input; only its type is logged.
        LOG.info("Unreadable request body: cause={} requestId={}", ex.getMostSpecificCause().getClass().getSimpleName(), requestId());
        return respond(headers, status, ApiError.ofStatus(400, requestId()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> fields = ex.getConstraintViolations().stream().map(violation -> violation.getPropertyPath().toString())
                .distinct().sorted().toList();
        LOG.info("Invalid request: exception={} fields={} requestId={}", ex.getClass().getSimpleName(), fields, requestId());
        return json(HttpStatus.BAD_REQUEST, new ApiError(ApiError.INVALID_REQUEST, "Invalid request", requestId(), fields));
    }

    /** Every other Spring MVC error (404, 405, 406, 415, type mismatches, ...) ends here with the generic body of its status. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            logUnexpected(ex);
        }
        return respond(headers, statusCode, ApiError.ofStatus(statusCode.value(), requestId()));
    }

    // ---- everything else -----------------------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        logUnexpected(ex);
        return json(HttpStatus.INTERNAL_SERVER_ERROR, ApiError.internal(requestId()));
    }

    private static void logUnexpected(Exception ex) {
        StackTraceElement origin = ex.getStackTrace().length > 0 ? ex.getStackTrace()[0] : null;
        LOG.error("Unhandled exception: class={} at={} requestId={}", ex.getClass().getName(),
                origin == null ? "unknown" : origin.getClassName() + "." + origin.getMethodName() + ":" + origin.getLineNumber(),
                requestId());
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private static boolean isCausedBy(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    private static String requestId() {
        return RequestIds.current();
    }

    private static ResponseEntity<ApiError> json(HttpStatus status, ApiError body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static ResponseEntity<Object> respond(HttpHeaders headers, HttpStatusCode status, ApiError body) {
        HttpHeaders withType = new HttpHeaders();
        if (headers != null) {
            withType.putAll(headers);
        }
        withType.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(body, withType, status);
    }
}
