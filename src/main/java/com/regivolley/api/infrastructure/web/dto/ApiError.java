package com.regivolley.api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The one error body of the API (threat model section 7): a stable {@code code} chosen by explicit mapping, a message that is
 * either generic or a business rule's own English text (architecture.md section 12), and the id of the request for the
 * logs. {@code fields} names the offending input fields on a 400, never their values. No stack trace, no personal data.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, String requestId, List<String> fields) {

    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String INVALID_LINK = "INVALID_LINK";
    public static final String TOO_MANY_REQUESTS = "TOO_MANY_REQUESTS";
    public static final String SERVICE_BUSY = "SERVICE_BUSY";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_ALLOWED = "NOT_ALLOWED";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String BUSINESS_RULE_VIOLATION = "BUSINESS_RULE_VIOLATION";
    public static final String INVALID_FIELD = "INVALID_FIELD";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String NOT_ACCEPTABLE = "NOT_ACCEPTABLE";
    public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";
    public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
    public static final String LENGTH_REQUIRED = "LENGTH_REQUIRED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    public ApiError(String code, String message, String requestId) {
        this(code, message, requestId, null);
    }

    public static ApiError unauthenticated(String requestId) {
        return new ApiError(UNAUTHENTICATED, "Authentication is required", requestId);
    }

    /** The one answer to a failed login, whatever the reason (threat model D-10). */
    public static ApiError invalidCredentials(String requestId) {
        return new ApiError(INVALID_CREDENTIALS, "Invalid email or password", requestId);
    }

    public static ApiError invalidLink(String requestId) {
        return new ApiError(INVALID_LINK, "The link is invalid or has expired", requestId);
    }

    public static ApiError tooManyRequests(String requestId) {
        return new ApiError(TOO_MANY_REQUESTS, "Too many requests, try again later", requestId);
    }

    public static ApiError serviceBusy(String requestId) {
        return new ApiError(SERVICE_BUSY, "The service is busy, try again shortly", requestId);
    }

    public static ApiError forbidden(String requestId) {
        return new ApiError(FORBIDDEN, "Access is denied", requestId);
    }

    public static ApiError internal(String requestId) {
        return new ApiError(INTERNAL_ERROR, "An unexpected error occurred", requestId);
    }

    /** The generic error for an HTTP status that no business rule produced (framework and container errors). */
    public static ApiError ofStatus(int status, String requestId) {
        return switch (status) {
            case 400 -> new ApiError(INVALID_REQUEST, "Invalid request", requestId);
            case 401 -> unauthenticated(requestId);
            case 403 -> forbidden(requestId);
            case 404 -> new ApiError(NOT_FOUND, "The requested resource was not found", requestId);
            case 405 -> new ApiError(METHOD_NOT_ALLOWED, "This method is not supported here", requestId);
            case 406 -> new ApiError(NOT_ACCEPTABLE, "The requested representation is not available", requestId);
            case 503 -> serviceBusy(requestId);
            case 429 -> tooManyRequests(requestId);
            case 411 -> new ApiError(LENGTH_REQUIRED, "The request body must declare its length", requestId);
            case 413 -> new ApiError(PAYLOAD_TOO_LARGE, "The request body is too large", requestId);
            case 415 -> new ApiError(UNSUPPORTED_MEDIA_TYPE, "The content type is not supported", requestId);
            default -> status >= 400 && status < 500
                    ? new ApiError(INVALID_REQUEST, "Invalid request", requestId)
                    : internal(requestId);
        };
    }
}
