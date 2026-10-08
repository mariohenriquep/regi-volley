package com.regivolley.api.infrastructure.security;

import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Test double of the seam 26b implements with {@code app_user} and {@code membership}: accounts are registered by hand.
 * Not found means exactly what it means in production: no such user, or no membership of that user at that member.
 */
public class InMemorySecurityAccountLookup implements SecurityAccountLookup {

    private record Key(UUID userId, AssociationId associationId, MemberId memberId) {
    }

    private final Map<Key, SecurityAccount> accounts = new HashMap<>();

    public void register(UUID userId, AssociationId associationId, MemberId memberId, SecurityAccount account) {
        accounts.put(new Key(userId, associationId, memberId), account);
    }

    public void registerActive(UUID userId, AssociationId associationId, MemberId memberId, String stamp) {
        register(userId, associationId, memberId, new SecurityAccount(UserStatus.ACTIVE, stamp, MembershipStatus.CONFIRMED));
    }

    public void clear() {
        accounts.clear();
    }

    @Override
    public Optional<SecurityAccount> find(UUID userId, AssociationId associationId, MemberId memberId) {
        return Optional.ofNullable(accounts.get(new Key(userId, associationId, memberId)));
    }
}
