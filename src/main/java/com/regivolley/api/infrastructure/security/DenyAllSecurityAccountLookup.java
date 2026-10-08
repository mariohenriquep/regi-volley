package com.regivolley.api.infrastructure.security;

import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;

import java.util.Optional;
import java.util.UUID;

/**
 * The lookup used until 26b provides the real one: no account exists, so every token is rejected. Fails closed. {@link SecurityConfiguration}
 * falls back to it only when no other {@link SecurityAccountLookup} bean exists (an {@code ObjectProvider}), so 26b's
 * implementation replaces it by merely being a bean.
 */
public class DenyAllSecurityAccountLookup implements SecurityAccountLookup {

    @Override
    public Optional<SecurityAccount> find(UUID userId, AssociationId associationId, MemberId memberId) {
        return Optional.empty();
    }
}
