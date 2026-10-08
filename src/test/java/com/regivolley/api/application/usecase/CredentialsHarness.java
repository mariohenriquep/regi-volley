package com.regivolley.api.application.usecase;

import com.regivolley.api.application.identity.AccessToken;
import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.identity.MembershipStatus;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.identity.UserStatus;
import com.regivolley.api.application.port.AccessTokenIssuer;
import com.regivolley.api.application.port.PasswordHasher;
import com.regivolley.api.application.port.PrincipalVerifier;
import com.regivolley.api.application.port.SecretGenerator;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.testsupport.MutableClock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The collaborators of the credential use cases wired for unit tests: in-memory stores, a clock tests move, a cheap hasher that
 * still has a current and a legacy format, a verifier with the same rules as the real principal resolver, a token issuer that
 * returns opaque strings, real-looking secrets, and an active member in an association.
 */
final class CredentialsHarness {

    static final Instant NOW = Instant.parse("2026-10-12T09:00:00Z");
    static final String PASSWORD = "correct horse battery staple";
    static final String EMAIL = "ana.silva@example.com";

    final MutableClock clock = new MutableClock(NOW);
    final InMemoryCredentialStores stores = new InMemoryCredentialStores();
    final MemberRepository members = mock(MemberRepository.class);
    final AssociationRepository associations = mock(AssociationRepository.class);
    final DirectTransactions transactions = new DirectTransactions();
    final PasswordHasher hasher = new FastPasswordHasher();
    final SecretGenerator secrets = new TestSecrets();
    final PrincipalVerifier verifier = new StoresBackedVerifier();
    final AccessTokenIssuer accessTokens = (userId, associationId, memberId, stamp) ->
            new AccessToken("access-" + userId + "-" + stamp, clock.instant().plus(Duration.ofMinutes(10)));

    final Association association = Data.association();
    Member member = Data.member(association);

    CredentialsHarness() {
        when(members.findById(association.id(), member.id())).thenReturn(Optional.of(member));
        when(associations.findById(association.id())).thenReturn(Optional.of(association));
    }

    /** An activated user (password set) with a CONFIRMED membership of the seeded member. */
    UserAccount confirmedUser() {
        UserAccount fresh = UserAccount.create(EmailAddress.of(EMAIL), secrets.newSecret(), NOW);
        UserAccount user = new UserAccount(fresh.id(), fresh.email(), UserStatus.ACTIVE, hasher.hash(PASSWORD), fresh.securityStamp(), NOW, NOW);
        stores.users.insert(user);
        stores.memberships.insert(new Membership(UUID.randomUUID(), user.id(), association.id(), member.id(),
                MembershipStatus.CONFIRMED, NOW, NOW));
        return user;
    }

    /** A user who was provisioned but has not activated: no password, PENDING membership. */
    UserAccount pendingUser() {
        UserAccount user = UserAccount.create(EmailAddress.of(EMAIL), secrets.newSecret(), NOW);
        stores.users.insert(user);
        stores.memberships.insert(Membership.pending(user.id(), association.id(), member.id(), NOW));
        return user;
    }

    Membership membershipOf(UserAccount user) {
        return stores.memberships.findByUserAndAssociation(user.id(), association.id()).orElseThrow();
    }

    /** A cheap two-format hasher: {@code {fast}f:<raw>} is the current format and {@code {legacy}l:<raw>} the legacy one. */
    static final class FastPasswordHasher implements PasswordHasher {
        private static final String CURRENT = "{fast}f:";
        private static final String LEGACY = "{legacy}l:";

        @Override
        public String hash(String password) {
            return CURRENT + password;
        }

        @Override
        public boolean matches(String password, String storedHash) {
            return storedHash.equals(CURRENT + password) || storedHash.equals(LEGACY + password);
        }

        @Override
        public boolean needsUpgrade(String storedHash) {
            return storedHash.startsWith(LEGACY);
        }

        @Override
        public void burn(String password) {
            matches(password, CURRENT + "dummy");
        }
    }

    /** 256 random bits, base64url, and SHA-256 hex: the shapes the real generator produces. */
    static final class TestSecrets implements SecretGenerator {
        private final SecureRandom random = new SecureRandom();

        @Override
        public String newSecret() {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }

        @Override
        public String hash(String secret) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** The rules of the real principal resolver, over the in-memory stores and the member mock. */
    private final class StoresBackedVerifier implements PrincipalVerifier {
        @Override
        public Optional<String> rejectionReason(UUID userId, AssociationId associationId, MemberId memberId, String securityStamp) {
            Optional<Membership> membership = stores.memberships.findByMember(associationId, memberId).filter(m -> m.userId().equals(userId));
            Optional<UserAccount> user = stores.users.findById(userId);
            if (membership.isEmpty() || user.isEmpty()) {
                return Optional.of("UNKNOWN_ACCOUNT");
            }
            if (user.get().status() != UserStatus.ACTIVE) {
                return Optional.of("ACCOUNT_DISABLED");
            }
            if (!user.get().securityStamp().equals(securityStamp)) {
                return Optional.of("STAMP_MISMATCH");
            }
            if (membership.get().status() != MembershipStatus.CONFIRMED) {
                return Optional.of("MEMBERSHIP_NOT_CONFIRMED");
            }
            Optional<Member> found = members.findById(associationId, memberId);
            if (found.isEmpty()) {
                return Optional.of("MEMBER_NOT_FOUND");
            }
            if (found.get().isAnonymised()) {
                return Optional.of("MEMBER_ANONYMISED");
            }
            return found.get().isActive() ? Optional.empty() : Optional.of("MEMBER_INACTIVE");
        }
    }
}
