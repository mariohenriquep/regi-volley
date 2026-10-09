package com.regivolley.api.infrastructure.web.dto;

/**
 * The answer to a login or a refresh: the bearer access token for the {@code Authorization} header and its lifetime in seconds.
 * The refresh token is never in the body; it travels in an HttpOnly cookie (threat model D-7a). A credential: never printed.
 */
public record AccessTokenResponse(String accessToken, String tokenType, long expiresIn) {

    @Override
    public String toString() {
        return "AccessTokenResponse[redacted]";
    }
}
