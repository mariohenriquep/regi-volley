package com.regivolley.api.infrastructure.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/** Last step of token authentication: the verified JWT is handed to the {@link PrincipalResolver}, which may still refuse it. */
class ActorAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final PrincipalResolver resolver;

    ActorAuthenticationConverter(PrincipalResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new ActorAuthenticationToken(resolver.resolve(jwt));
    }
}
