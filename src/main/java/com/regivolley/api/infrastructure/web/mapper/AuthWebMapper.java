package com.regivolley.api.infrastructure.web.mapper;

import com.regivolley.api.application.result.SessionTokens;
import com.regivolley.api.infrastructure.web.dto.AccessTokenResponse;

/** The tokens of a login or refresh to the body the client sees; the refresh token is deliberately left out (it goes in the cookie). */
public final class AuthWebMapper {

    private static final String BEARER = "Bearer";

    private AuthWebMapper() {
    }

    public static AccessTokenResponse toResponse(SessionTokens tokens) {
        return new AccessTokenResponse(tokens.accessToken(), BEARER, tokens.expiresInSeconds());
    }
}
