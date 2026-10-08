package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.identity.MembershipStatus;
import com.regivolley.api.application.identity.UserStatus;
import com.regivolley.api.application.command.Actor;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.function.Executable;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** D-6: every request is re-checked against the account, the membership and the member, so revocation is immediate. */
class PrincipalResolverTest {

    private static final String STAMP = "stamp-1";

    private final MemberRepository members = mock(MemberRepository.class);
    private final InMemorySecurityAccountLookup accounts = new InMemorySecurityAccountLookup();
    private final PrincipalResolver resolver = new PrincipalResolver(accounts, members);

    private Association association;
    private Member member;
    private UUID userId;

    @BeforeEach
    void setUp() {
        association = SecurityFixtures.association();
        member = SecurityFixtures.active(association);
        userId = UUID.randomUUID();
        accounts.registerActive(userId, association.id(), member.id(), STAMP);
        when(members.findById(association.id(), member.id())).thenReturn(Optional.of(member));
    }

    private Jwt token(String subject, Object aid, Object mid, Object stamp) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "ES256")
                .issuedAt(Instant.parse("2026-10-12T09:00:00Z")).expiresAt(Instant.parse("2026-10-12T09:10:00Z"));
        if (subject != null) {
            builder.subject(subject);
        }
        if (aid != null) {
            builder.claim("aid", aid);
        }
        if (mid != null) {
            builder.claim("mid", mid);
        }
        if (stamp != null) {
            builder.claim("sv", stamp);
        }
        return builder.build();
    }

    private Jwt validToken() {
        return token(userId.toString(), association.id().toString(), member.id().toString(), STAMP);
    }

    private PrincipalRejectedException rejection(Jwt jwt) {
        Executable act = () -> resolver.resolve(jwt);
        return assertThrows(PrincipalRejectedException.class, act);
    }

    @Test
    void resolvesAnActiveConfirmedMemberToTheirActor() {
        // Arrange
        Jwt jwt = validToken();

        // Act
        AuthenticatedActor principal = resolver.resolve(jwt);

        // Assert
        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.actor()).isEqualTo(new Actor(association.id(), member.id()));
    }

    @Test
    void rejectsAnUnknownAccountOrAMembershipOfAnotherUser() {
        // Arrange
        Jwt unknownUser = token(UUID.randomUUID().toString(), association.id().toString(), member.id().toString(), STAMP);

        // Act
        PrincipalRejectedException e = rejection(unknownUser);

        // Assert
        assertThat(e.reason()).isEqualTo(PrincipalRejection.UNKNOWN_ACCOUNT);
    }

    @Test
    void rejectsADisabledAccount() {
        // Arrange
        accounts.register(userId, association.id(), member.id(), UserStatus.DISABLED, STAMP, MembershipStatus.CONFIRMED);

        // Act
        PrincipalRejectedException e = rejection(validToken());

        // Assert
        assertThat(e.reason()).isEqualTo(PrincipalRejection.ACCOUNT_DISABLED);
    }

    @Test
    void rejectsATokenWhoseSecurityStampIsNoLongerTheStoredOne() {
        // Arrange
        accounts.registerActive(userId, association.id(), member.id(), "stamp-2");

        // Act
        PrincipalRejectedException e = rejection(validToken());

        // Assert
        assertThat(e.reason()).isEqualTo(PrincipalRejection.STAMP_MISMATCH);
    }

    @Test
    void rejectsAMembershipThatIsStillPending() {
        // Arrange
        accounts.register(userId, association.id(), member.id(), UserStatus.ACTIVE, STAMP, MembershipStatus.PENDING);

        // Act
        PrincipalRejectedException e = rejection(validToken());

        // Assert
        assertThat(e.reason()).isEqualTo(PrincipalRejection.MEMBERSHIP_NOT_CONFIRMED);
    }

    @Test
    void rejectsAMemberTheAssociationDoesNotHave() {
        // Arrange
        when(members.findById(association.id(), member.id())).thenReturn(Optional.empty());

        // Act
        PrincipalRejectedException e = rejection(validToken());

        // Assert
        assertThat(e.reason()).isEqualTo(PrincipalRejection.MEMBER_NOT_FOUND);
    }

    @Test
    void rejectsADeactivatedMemberImmediately() {
        // Arrange
        when(members.findById(association.id(), member.id())).thenReturn(Optional.of(member.deactivate()));

        // Act
        PrincipalRejectedException e = rejection(validToken());

        // Assert
        assertThat(e.reason()).isEqualTo(PrincipalRejection.MEMBER_INACTIVE);
    }

    @Test
    void rejectsAnAnonymisedMemberImmediately() {
        // Arrange
        when(members.findById(association.id(), member.id())).thenReturn(Optional.of(member.anonymise(SecurityFixtures.CLOCK)));

        // Act
        PrincipalRejectedException e = rejection(validToken());

        // Assert
        assertThat(e.reason()).isEqualTo(PrincipalRejection.MEMBER_ANONYMISED);
    }

    @Test
    void rejectsAMembershipWhoseStoredTenantIsNotTheOneInTheToken() {
        // Arrange - a lookup that answered with a membership of another association (a bug or a bad join): refuse, never trust it
        accounts.register(userId, association.id(), member.id(),
                new SecurityAccount(UserStatus.ACTIVE, STAMP, MembershipStatus.CONFIRMED, AssociationId.generate(), member.id()));

        // Act
        PrincipalRejectedException e = rejection(validToken());

        // Assert
        assertThat(e.reason()).isEqualTo(PrincipalRejection.MEMBERSHIP_MISMATCH);
    }

    @Test
    void rejectsAMembershipWhoseStoredMemberIsNotTheOneInTheToken() {
        // Arrange
        accounts.register(userId, association.id(), member.id(),
                new SecurityAccount(UserStatus.ACTIVE, STAMP, MembershipStatus.CONFIRMED, association.id(), MemberId.generate()));

        // Act
        PrincipalRejectedException e = rejection(validToken());

        // Assert
        assertThat(e.reason()).isEqualTo(PrincipalRejection.MEMBERSHIP_MISMATCH);
    }

    @Test
    void resolvesFromExplicitIdsForTheRefreshPath() {
        // Arrange
        // (the seeded active member)

        // Act
        AuthenticatedActor principal = resolver.resolve(userId, association.id(), member.id(), STAMP);

        // Assert
        assertThat(principal).isEqualTo(new AuthenticatedActor(userId, association.id(), member.id()));
    }

    @Test
    void rejectsAMemberLoadedFromAnotherTenantDefensively() {
        // Arrange
        Member foreign = SecurityFixtures.active(SecurityFixtures.association());
        when(members.findById(association.id(), member.id())).thenReturn(Optional.of(foreign));

        // Act
        PrincipalRejectedException e = rejection(validToken());

        // Assert
        assertThat(e.reason()).isEqualTo(PrincipalRejection.MEMBER_NOT_FOUND);
    }

    static Stream<Arguments> malformedClaims() {
        String aid = UUID.randomUUID().toString();
        String mid = UUID.randomUUID().toString();
        String user = UUID.randomUUID().toString();
        return Stream.of(
                Arguments.of("subject is not a UUID", "not-a-uuid", aid, mid, STAMP),
                Arguments.of("aid is not a UUID", user, "not-a-uuid", mid, STAMP),
                Arguments.of("mid is not a UUID", user, aid, "not-a-uuid", STAMP),
                Arguments.of("subject is missing", null, aid, mid, STAMP),
                Arguments.of("aid is missing", user, null, mid, STAMP),
                Arguments.of("mid is missing", user, aid, null, STAMP),
                Arguments.of("stamp is missing", user, aid, mid, null),
                Arguments.of("stamp is not text", user, aid, mid, 42));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedClaims")
    void rejectsMalformedClaims(String description, String subject, Object aid, Object mid, Object stamp) {
        // Arrange
        Jwt jwt = token(subject, aid, mid, stamp);

        // Act
        PrincipalRejectedException e = rejection(jwt);

        // Assert
        assertThat(e.reason()).isEqualTo(PrincipalRejection.MALFORMED_CLAIMS);
    }

    @Test
    void theRejectionMessageIsConstantAndCarriesNoIds() {
        // Arrange
        accounts.clear();

        // Act
        PrincipalRejectedException e = rejection(validToken());

        // Assert
        assertThat(e.getMessage()).doesNotContain(userId.toString()).doesNotContain(member.id().toString());
    }
}
