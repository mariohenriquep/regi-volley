package com.regivolley.api.domain.factory;

import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.InvalidMemberException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.entity.Level;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.GdprConsent;
import com.regivolley.api.domain.model.valueobject.LevelChange;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.MemberStatus;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemberFactoryTest {

    private static final Instant REQUESTED = Instant.parse("2026-10-07T20:00:00Z");

    private static final Instant DECIDED = Instant.parse("2026-10-08T09:15:00Z");

    private static final Clock REQUEST_CLOCK = Clock.fixed(REQUESTED, ZoneOffset.UTC);

    private static final Clock DECISION_CLOCK = Clock.fixed(DECIDED, ZoneOffset.UTC);

    private static final MemberId ADMIN = MemberId.generate();

    private static final Instant JOINED = Instant.parse("2026-10-07T20:00:00Z");

    private static JoinRequest pending() {
        return JoinRequestFactory.create(ASSOCIATION.id(), CONTACT, true, "2026-10", REQUEST_CLOCK);
    }

    private static final Instant LATER = Instant.parse("2026-10-20T18:30:00Z");

    private static final Clock JOIN_CLOCK = Clock.fixed(JOINED, ZoneOffset.UTC);

    private static final GdprConsent CONSENT = new GdprConsent(JOINED, "2026-10");

    private static final Association ASSOCIATION = AssociationFactory.create("Club", "club", null, "Lisbon", "a@b.co",
            List.of("Beginner", "Intermediate", "Advanced"));

    private static final Level BEGINNER = ASSOCIATION.levels().get(0);

    private static final Level INTERMEDIATE = ASSOCIATION.levels().get(1);

    private static final Level ADVANCED = ASSOCIATION.levels().get(2);

    private static final MemberId COACH = MemberId.generate();

    private static final ContactDetails CONTACT = ContactDetails.of("Ana Silva", EmailAddress.of("ana@example.com"),
            PhoneNumber.of("912345678"));

    private static Member member() {
        return MemberFactory.create(ASSOCIATION, CONTACT, CONSENT, Set.of(MemberRole.MEMBER), JOIN_CLOCK);
    }

    @Test
    void anApprovedRequestBecomesAnActiveMemberAtTheEntryLevel() {
        // Arrange
        JoinRequest approved = pending().approve(ADMIN, DECISION_CLOCK);

        // Act
        Member member = MemberFactory.fromApprovedJoinRequest(ASSOCIATION, approved);

        // Assert
        assertThat(member.associationId()).isEqualTo(ASSOCIATION.id());
        assertThat(member.levelId()).isEqualTo(ASSOCIATION.entryLevelId());
        assertThat(member.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.roles()).containsExactly(MemberRole.MEMBER);
        assertThat(member.levelChanges()).isEmpty();
        assertThat(member.version()).isZero();
    }

    @Test
    void theMemberCarriesTheRequestsContactDataAndConsent() {
        // Arrange
        JoinRequest approved = pending().approve(ADMIN, DECISION_CLOCK);

        // Act
        Member member = MemberFactory.fromApprovedJoinRequest(ASSOCIATION, approved);

        // Assert
        assertThat(member.contact()).isEqualTo(approved.contact());
        assertThat(member.consent()).isEqualTo(approved.consent());
    }

    @Test
    void theMemberJoinsAtTheInstantOfTheDecision() {
        // Arrange
        JoinRequest approved = pending().approve(ADMIN, DECISION_CLOCK);

        // Act
        Member member = MemberFactory.fromApprovedJoinRequest(ASSOCIATION, approved);

        // Assert
        assertThat(member.joinedAt()).isEqualTo(DECIDED).isEqualTo(approved.decidedAt().orElseThrow());
    }

    @Test
    void aNewMemberStartsAtTheEntryLevelWhateverItsPosition() {
        // Arrange
        Association topEntry = ASSOCIATION.changeEntryLevel(ASSOCIATION.levels().get(1).id());
        JoinRequest approved = pending().approve(ADMIN, DECISION_CLOCK);

        // Act
        Member member = MemberFactory.fromApprovedJoinRequest(topEntry, approved);

        // Assert
        assertThat(member.levelId()).isEqualTo(topEntry.levels().get(1).id());
    }

    @Test
    void rejectsAnAssociationThatIsNotTheRequests() {
        // Arrange
        Association other = AssociationFactory.create("Other", "other", null, "Porto", "x@y.co", List.of("Open"));
        JoinRequest approved = pending().approve(ADMIN, DECISION_CLOCK);
        Executable act = () -> MemberFactory.fromApprovedJoinRequest(other, approved);

        // Act
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("another association");
    }

    @Test
    void aRequestThatIsNotApprovedDoesNotBecomeAMember() {
        // Arrange
        JoinRequest stillPending = pending();
        JoinRequest rejected = pending().reject(ADMIN, "Full", DECISION_CLOCK);
        Executable pendingAct = () -> MemberFactory.fromApprovedJoinRequest(ASSOCIATION, stillPending);
        Executable rejectedAct = () -> MemberFactory.fromApprovedJoinRequest(ASSOCIATION, rejected);

        // Act
        IllegalArgumentException fromPending = assertThrows(IllegalArgumentException.class, pendingAct);
        IllegalArgumentException fromRejected = assertThrows(IllegalArgumentException.class, rejectedAct);

        // Assert
        assertThat(fromPending.getMessage()).contains("approved");
        assertThat(fromRejected.getMessage()).contains("approved");
    }

    @Nested
    class Creation {

        @Test
        void startsActiveAtTheEntryLevelWithNoHistory() {
            // Arrange
            // (default fixtures)

            // Act
            Member member = member();

            // Assert
            assertThat(member.id()).isNotNull();
            assertThat(member.associationId()).isEqualTo(ASSOCIATION.id());
            assertThat(member.name()).isEqualTo("Ana Silva");
            assertThat(member.email()).isEqualTo(EmailAddress.of("ana@example.com"));
            assertThat(member.phone()).contains(PhoneNumber.of("912345678"));
            assertThat(member.contact()).isEqualTo(CONTACT);
            assertThat(member.consent()).isEqualTo(CONSENT);
            assertThat(member.status()).isEqualTo(MemberStatus.ACTIVE);
            assertThat(member.isActive()).isTrue();
            assertThat(member.levelId()).isEqualTo(ASSOCIATION.entryLevelId());
            assertThat(member.roles()).containsExactly(MemberRole.MEMBER);
            assertThat(member.levelChanges()).isEmpty();
            assertThat(member.joinedAt()).isEqualTo(JOINED);
            assertThat(member.anonymisedAt()).isEmpty();
            assertThat(member.isAnonymised()).isFalse();
        }

        @Test
        void startsAtTheEntryLevelEvenWhenItIsNotTheLowest() {
            // Arrange
            Association advancedEntry = ASSOCIATION.changeEntryLevel(ADVANCED.id());

            // Act
            Member member = MemberFactory.create(advancedEntry, ContactDetails.of("Ana", EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), CONSENT, Set.of(MemberRole.MEMBER), JOIN_CLOCK);

            // Assert
            assertThat(member.levelId()).isEqualTo(ADVANCED.id());
        }

        @Test
        void trimsTheName() {
            // Arrange
            // (padded name)

            // Act
            Member member = MemberFactory.create(ASSOCIATION, ContactDetails.of("  Ana  ", EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), CONSENT, Set.of(MemberRole.ADMIN), JOIN_CLOCK);

            // Assert
            assertThat(member.name()).isEqualTo("Ana");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void requiresAName(String name) {
            // Arrange
            Executable act = () -> MemberFactory.create(ASSOCIATION, ContactDetails.of(name, EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), CONSENT, Set.of(MemberRole.MEMBER), JOIN_CLOCK);

            // Act
            InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

            // Assert
            assertThat(ex.field()).isEqualTo("name");
        }

        @Test
        void acceptsANameOfTheMaximumLength() {
            // Arrange
            String atMax = "x".repeat(ContactDetails.MAX_NAME_LENGTH);

            // Act
            Member member = MemberFactory.create(ASSOCIATION, ContactDetails.of(atMax, EmailAddress.of("ana@example.com"),
                    PhoneNumber.of("912345678")), CONSENT, Set.of(MemberRole.MEMBER), JOIN_CLOCK);

            // Assert
            assertThat(member.name()).hasSize(ContactDetails.MAX_NAME_LENGTH);
        }

        @Test
        void rejectsANameThatIsTooLong() {
            // Arrange
            String tooLong = "x".repeat(ContactDetails.MAX_NAME_LENGTH + 1);
            Executable act = () -> MemberFactory.create(ASSOCIATION, ContactDetails.of(tooLong, EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), CONSENT, Set.of(MemberRole.MEMBER), JOIN_CLOCK);

            // Act
            InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("100");
        }

        @Test
        void requiresAtLeastOneRole() {
            // Arrange
            Executable act = () -> MemberFactory.create(ASSOCIATION, ContactDetails.of("Ana", EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), CONSENT, Set.of(), JOIN_CLOCK);

            // Act
            InvalidMemberException ex = assertThrows(InvalidMemberException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("role");
        }

        @Test
        void requiresTheConsentRecord() {
            // Arrange
            Executable act = () -> MemberFactory.create(ASSOCIATION, ContactDetails.of("Ana", EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), null, Set.of(MemberRole.MEMBER), JOIN_CLOCK);

            // Act
            NullPointerException ex = assertThrows(NullPointerException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("consent");
        }

        @Test
        void aPersonCanBeCoachAndMember() {
            // Arrange
            Set<MemberRole> roles = Set.of(MemberRole.MEMBER, MemberRole.COACH);

            // Act
            Member member = MemberFactory.create(ASSOCIATION, ContactDetails.of("Rui", EmailAddress.of("rui@example.com"), PhoneNumber.of("912345679")), CONSENT, roles, JOIN_CLOCK);

            // Assert
            assertThat(member.hasRole(MemberRole.COACH)).isTrue();
            assertThat(member.hasRole(MemberRole.MEMBER)).isTrue();
            assertThat(member.hasRole(MemberRole.ADMIN)).isFalse();
        }
    }

    @Nested
    class Reconstruction {

        private Member rebuild(MemberStatus status, LevelId level, List<LevelChange> changes, Instant anonymisedAt) {
            return MemberFactory.reconstitute(MemberId.generate(), ASSOCIATION.id(), CONTACT, CONSENT, status, level, Set.of(MemberRole.MEMBER), changes, JOINED,
                    anonymisedAt, 0L);
        }

        @Test
        void keepsTheVersionItWasLoadedWithThroughEveryChange() {
            // Arrange
            Member loaded = MemberFactory.reconstitute(MemberId.generate(), ASSOCIATION.id(), CONTACT, CONSENT,
                    MemberStatus.ACTIVE, BEGINNER.id(), Set.of(MemberRole.MEMBER), List.of(), JOINED, null, 6L);

            // Act
            Member edited = loaded.grantRole(MemberRole.COACH).deactivate().reactivate();
            Member erased = edited.anonymise(Clock.fixed(JOINED.plusSeconds(60), ZoneOffset.UTC));

            // Assert
            assertThat(loaded.version()).isEqualTo(6L);
            assertThat(edited.version()).isEqualTo(6L);
            assertThat(erased.version()).isEqualTo(6L);
        }

        @Test
        void aNewMemberStartsAtVersionZero() {
            // Arrange
            // (default fixtures)

            // Act
            Member member = MemberFactory.create(ASSOCIATION, CONTACT, CONSENT, Set.of(MemberRole.MEMBER), JOIN_CLOCK);

            // Assert
            assertThat(member.version()).isZero();
        }

        @Test
        void rejectsANegativeVersion() {
            // Arrange
            Executable act = () -> MemberFactory.reconstitute(MemberId.generate(), ASSOCIATION.id(), CONTACT, CONSENT,
                    MemberStatus.ACTIVE, BEGINNER.id(), Set.of(MemberRole.MEMBER), List.of(), JOINED, null, -1L);

            // Act
            InvalidMemberException ex = assertThrows(InvalidMemberException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("version");
        }

        @Test
        void acceptsAContinuousHistoryEndingAtTheCurrentLevel() {
            // Arrange
            List<LevelChange> history = List.of(
                    new LevelChange(BEGINNER.id(), INTERMEDIATE.id(), COACH, JOINED),
                    new LevelChange(INTERMEDIATE.id(), ADVANCED.id(), COACH, LATER));

            // Act
            Member member = rebuild(MemberStatus.ACTIVE, ADVANCED.id(), history, null);

            // Assert
            assertThat(member.levelChanges()).isEqualTo(history);
        }

        @Test
        void rejectsAHistoryThatDoesNotEndAtTheCurrentLevel() {
            // Arrange
            List<LevelChange> history = List.of(new LevelChange(BEGINNER.id(), INTERMEDIATE.id(), COACH, JOINED));
            Executable act = () -> rebuild(MemberStatus.ACTIVE, ADVANCED.id(), history, null);

            // Act
            InvalidMemberException ex = assertThrows(InvalidMemberException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("current level");
        }

        @Test
        void rejectsAHistoryWithABrokenChain() {
            // Arrange
            List<LevelChange> history = List.of(
                    new LevelChange(BEGINNER.id(), INTERMEDIATE.id(), COACH, JOINED),
                    new LevelChange(BEGINNER.id(), ADVANCED.id(), COACH, LATER));
            Executable act = () -> rebuild(MemberStatus.ACTIVE, ADVANCED.id(), history, null);

            // Act
            InvalidMemberException ex = assertThrows(InvalidMemberException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("continuous");
        }

        @Test
        void rejectsAHistoryOutOfChronologicalOrder() {
            // Arrange
            List<LevelChange> history = List.of(
                    new LevelChange(BEGINNER.id(), INTERMEDIATE.id(), COACH, LATER),
                    new LevelChange(INTERMEDIATE.id(), ADVANCED.id(), COACH, JOINED));
            Executable act = () -> rebuild(MemberStatus.ACTIVE, ADVANCED.id(), history, null);

            // Act
            InvalidMemberException ex = assertThrows(InvalidMemberException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("chronological");
        }

        @Test
        void rejectsAHistoryEntryBeforeTheMemberJoined() {
            // Arrange
            List<LevelChange> history = List.of(
                    new LevelChange(BEGINNER.id(), INTERMEDIATE.id(), COACH, JOINED.minusSeconds(1)));
            Executable act = () -> rebuild(MemberStatus.ACTIVE, INTERMEDIATE.id(), history, null);

            // Act
            InvalidMemberException ex = assertThrows(InvalidMemberException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("before the member joined");
        }

        @Test
        void rejectsAnAnonymisationBeforeTheMemberJoined() {
            // Arrange
            Executable act = () -> rebuild(MemberStatus.INACTIVE, BEGINNER.id(), List.of(), JOINED.minusSeconds(1));

            // Act
            InvalidMemberException ex = assertThrows(InvalidMemberException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("before the member joined");
        }

        @Test
        void aPhoneIsOnlyMissingOnAnErasedMember() {
            // Arrange
            ContactDetails withoutPhone = ContactDetails.reconstruct("Ana", EmailAddress.of("ana@example.com"), null);
            Executable act = () -> MemberFactory.reconstitute(MemberId.generate(), ASSOCIATION.id(), withoutPhone, CONSENT,
                    MemberStatus.ACTIVE, BEGINNER.id(), Set.of(MemberRole.MEMBER), List.of(), JOINED, null, 0L);

            // Act
            InvalidMemberException ex = assertThrows(InvalidMemberException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("phone");
        }

        @Test
        void rejectsAnAnonymisedMemberThatIsStillActive() {
            // Arrange
            Executable act = () -> rebuild(MemberStatus.ACTIVE, BEGINNER.id(), List.of(), LATER);

            // Act
            InvalidMemberException ex = assertThrows(InvalidMemberException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("inactive");
        }
    }
}
