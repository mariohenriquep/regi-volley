package com.regivolley.api.application.usecase;

import com.regivolley.api.application.exception.AccountAlreadyExistsException;
import com.regivolley.api.application.exception.LinkAlreadyIssuedException;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.application.identity.EmailLink;
import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.identity.MembershipStatus;
import com.regivolley.api.application.identity.RefreshToken;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.identity.UserStatus;
import com.regivolley.api.application.port.EmailLinkStore;
import com.regivolley.api.application.port.MembershipStore;
import com.regivolley.api.application.port.RefreshTokenStore;
import com.regivolley.api.application.port.UserAccountStore;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.MemberId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;



/**
 * In-memory twins of the four credential stores for the service unit tests. They keep the semantics the services depend on:
 * the conditional updates ({@code markUsed}, {@code consume}, {@code replacePasswordHash}) succeed once, a duplicate email is
 * refused, and a deleted account takes its memberships, tokens and links with it.
 */
final class InMemoryCredentialStores {

    final Users users = new Users();
    final Memberships memberships = new Memberships();
    final RefreshTokens refreshTokens = new RefreshTokens();
    final Links links = new Links();

    final class Users implements UserAccountStore {
        final Map<UUID, UserAccount> byId = new LinkedHashMap<>();

        @Override
        public Optional<UserAccount> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<UserAccount> findByEmail(EmailAddress email) {
            return byId.values().stream().filter(user -> user.email().equals(email)).findFirst();
        }

        @Override
        public UserAccount insert(UserAccount account) {
            if (findByEmail(account.email()).isPresent()) {
                throw new AccountAlreadyExistsException();
            }
            byId.put(account.id(), account);
            return account;
        }

        @Override
        public void setPassword(UUID userId, String passwordHash, String newSecurityStamp, Instant at) {
            UserAccount u = byId.get(userId);
            byId.put(userId, new UserAccount(u.id(), u.email(), u.status(), passwordHash, newSecurityStamp, u.createdAt(), at));
        }

        @Override
        public void rotateSecurityStamp(UUID userId, String newSecurityStamp, Instant at) {
            UserAccount u = byId.get(userId);
            byId.put(userId, new UserAccount(u.id(), u.email(), u.status(), u.passwordHash(), newSecurityStamp, u.createdAt(), at));
        }

        @Override
        public boolean replacePasswordHash(UUID userId, String expectedHash, String newHash, Instant at) {
            UserAccount u = byId.get(userId);
            if (u == null || !expectedHash.equals(u.passwordHash())) {
                return false;
            }
            byId.put(userId, new UserAccount(u.id(), u.email(), u.status(), newHash, u.securityStamp(), u.createdAt(), at));
            return true;
        }

        @Override
        public void deleteById(UUID userId) {
            byId.remove(userId);
            memberships.byId.values().removeIf(m -> m.userId().equals(userId));
            refreshTokens.byId.values().removeIf(t -> t.userId().equals(userId));
            links.byId.values().removeIf(l -> l.userId().equals(userId));
        }

        void disable(UUID userId) {
            UserAccount u = byId.get(userId);
            byId.put(userId, new UserAccount(u.id(), u.email(), UserStatus.DISABLED, u.passwordHash(), u.securityStamp(), u.createdAt(), u.updatedAt()));
        }
    }

    final class Memberships implements MembershipStore {
        final Map<UUID, Membership> byId = new LinkedHashMap<>();

        @Override
        public Optional<Membership> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<Membership> findByUser(UUID userId) {
            return byId.values().stream().filter(m -> m.userId().equals(userId)).toList();
        }

        @Override
        public Optional<Membership> findByUserAndAssociation(UUID userId, AssociationId associationId) {
            return byId.values().stream().filter(m -> m.userId().equals(userId) && m.associationId().equals(associationId)).findFirst();
        }

        @Override
        public Optional<Membership> findByMember(AssociationId associationId, MemberId memberId) {
            return byId.values().stream().filter(m -> m.associationId().equals(associationId) && m.memberId().equals(memberId)).findFirst();
        }

        @Override
        public Membership insert(Membership membership) {
            byId.put(membership.id(), membership);
            return membership;
        }

        @Override
        public boolean confirm(UUID membershipId, Instant at) {
            Membership m = byId.get(membershipId);
            if (m == null || m.isConfirmed()) {
                return false;
            }
            byId.put(membershipId, new Membership(m.id(), m.userId(), m.associationId(), m.memberId(), MembershipStatus.CONFIRMED, m.createdAt(), at));
            return true;
        }

        @Override
        public void deleteById(UUID membershipId) {
            byId.remove(membershipId);
            refreshTokens.byId.values().removeIf(t -> t.membershipId().equals(membershipId));
            links.byId.values().removeIf(l -> membershipId.equals(l.membershipId()));
        }
    }

    static final class RefreshTokens implements RefreshTokenStore {
        final Map<UUID, RefreshToken> byId = new LinkedHashMap<>();

        @Override
        public RefreshToken insert(RefreshToken token) {
            byId.put(token.id(), token);
            return token;
        }

        @Override
        public Optional<RefreshToken> findByTokenHash(String tokenHash) {
            return byId.values().stream().filter(t -> t.tokenHash().equals(tokenHash)).findFirst();
        }

        private void replace(RefreshToken t, Instant usedAt, Instant revokedAt) {
            byId.put(t.id(), new RefreshToken(t.id(), t.familyId(), t.userId(), t.membershipId(), t.tokenHash(), t.parentId(),
                    t.issuedAt(), t.expiresAt(), t.idleExpiresAt(), usedAt, revokedAt));
        }

        @Override
        public boolean markUsed(UUID tokenId, Instant at) {
            RefreshToken t = byId.get(tokenId);
            if (t == null || t.isUsed() || t.isRevoked()) {
                return false;
            }
            replace(t, at, t.revokedAt());
            return true;
        }

        @Override
        public int revokeFamily(UUID familyId, Instant at) {
            int count = 0;
            for (RefreshToken t : new ArrayList<>(byId.values())) {
                if (t.familyId().equals(familyId) && !t.isRevoked()) {
                    replace(t, t.usedAt(), at);
                    count++;
                }
            }
            return count;
        }

        @Override
        public int revokeAllOf(UUID userId, Instant at) {
            int count = 0;
            for (RefreshToken t : new ArrayList<>(byId.values())) {
                if (t.userId().equals(userId) && !t.isRevoked()) {
                    replace(t, t.usedAt(), at);
                    count++;
                }
            }
            return count;
        }

        @Override
        public int deleteExpiredOf(UUID userId, Instant now) {
            int before = byId.size();
            byId.values().removeIf(t -> t.userId().equals(userId) && (!now.isBefore(t.expiresAt()) || !now.isBefore(t.idleExpiresAt())));
            return before - byId.size();
        }

        List<RefreshToken> ofFamily(UUID familyId) {
            return byId.values().stream().filter(t -> t.familyId().equals(familyId)).toList();
        }
    }

    static final class Links implements EmailLinkStore {
        final Map<UUID, EmailLink> byId = new LinkedHashMap<>();

        @Override
        public EmailLink insert(EmailLink link) {
            // The partial unique indexes: one live ACTIVATION link per membership, one live PASSWORD_RESET link per user.
            boolean clash = byId.values().stream().anyMatch(other -> other.consumedAt() == null && other.purpose() == link.purpose()
                    && (link.purpose() == AccountLinkPurpose.ACTIVATION
                    ? link.membershipId().equals(other.membershipId()) : link.userId().equals(other.userId())));
            if (clash) {
                throw new LinkAlreadyIssuedException();
            }
            byId.put(link.id(), link);
            return link;
        }

        @Override
        public Optional<EmailLink> findByTokenHash(String tokenHash) {
            return byId.values().stream().filter(l -> l.tokenHash().equals(tokenHash)).findFirst();
        }

        private void consumed(EmailLink l, Instant at) {
            byId.put(l.id(), new EmailLink(l.id(), l.userId(), l.membershipId(), l.purpose(), l.tokenHash(), l.createdAt(), l.expiresAt(), at));
        }

        @Override
        public boolean consume(UUID linkId, Instant at) {
            EmailLink l = byId.get(linkId);
            if (l == null || l.consumedAt() != null) {
                return false;
            }
            consumed(l, at);
            return true;
        }

        @Override
        public int invalidateOpenForMembership(UUID membershipId, AccountLinkPurpose purpose, Instant at) {
            int count = 0;
            for (EmailLink l : new ArrayList<>(byId.values())) {
                if (membershipId.equals(l.membershipId()) && l.purpose() == purpose && l.consumedAt() == null) {
                    consumed(l, at);
                    count++;
                }
            }
            return count;
        }

        @Override
        public int invalidateOpenForUser(UUID userId, AccountLinkPurpose purpose, Instant at) {
            int count = 0;
            for (EmailLink l : new ArrayList<>(byId.values())) {
                if (l.userId().equals(userId) && l.purpose() == purpose && l.consumedAt() == null) {
                    consumed(l, at);
                    count++;
                }
            }
            return count;
        }

        @Override
        public int deleteSpentOf(UUID userId, Instant now) {
            int before = byId.size();
            byId.values().removeIf(l -> l.userId().equals(userId) && (l.consumedAt() != null || !now.isBefore(l.expiresAt())));
            return before - byId.size();
        }
    }
}
