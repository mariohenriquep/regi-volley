package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.InvalidMemberException;
import com.regivolley.api.domain.exception.InvalidMemberStatusTransitionException;
import com.regivolley.api.domain.exception.LastRoleCannotBeRevokedException;
import com.regivolley.api.domain.exception.LevelNotFoundException;
import com.regivolley.api.domain.exception.MemberAnonymisedException;
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

class MemberTest {

    private static final Instant JOINED = Instant.parse("2026-10-07T20:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-20T18:30:00Z");
    private static final Clock JOIN_CLOCK = Clock.fixed(JOINED, ZoneOffset.UTC);
    private static final Clock LATER_CLOCK = Clock.fixed(LATER, ZoneOffset.UTC);
    private static final GdprConsent CONSENT = new GdprConsent(JOINED, "2026-10");

    private static final Association ASSOCIATION = Association.create("Club", "club", null, "Lisbon", "a@b.co",
            List.of("Beginner", "Intermediate", "Advanced"));
    private static final Level BEGINNER = ASSOCIATION.levels().get(0);
    private static final Level INTERMEDIATE = ASSOCIATION.levels().get(1);
    private static final Level ADVANCED = ASSOCIATION.levels().get(2);
    private static final MemberId COACH = MemberId.generate();

    private static final ContactDetails CONTACT = ContactDetails.of("Ana Silva", EmailAddress.of("ana@example.com"),
            PhoneNumber.of("912345678"));

    private static Member member() {
        return Member.create(ASSOCIATION, CONTACT, CONSENT, Set.of(MemberRole.MEMBER), JOIN_CLOCK);
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
            Member member = Member.create(advancedEntry, ContactDetails.of("Ana", EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), CONSENT, Set.of(MemberRole.MEMBER), JOIN_CLOCK);

            // Assert
            assertThat(member.levelId()).isEqualTo(ADVANCED.id());
        }

        @Test
        void trimsTheName() {
            // Arrange
            // (padded name)

            // Act
            Member member = Member.create(ASSOCIATION, ContactDetails.of("  Ana  ", EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), CONSENT, Set.of(MemberRole.ADMIN), JOIN_CLOCK);

            // Assert
            assertThat(member.name()).isEqualTo("Ana");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void requiresAName(String name) {
            // Arrange
            Executable act = () -> Member.create(ASSOCIATION, ContactDetails.of(name, EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), CONSENT, Set.of(MemberRole.MEMBER), JOIN_CLOCK);

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
            Member member = Member.create(ASSOCIATION, ContactDetails.of(atMax, EmailAddress.of("ana@example.com"),
                    PhoneNumber.of("912345678")), CONSENT, Set.of(MemberRole.MEMBER), JOIN_CLOCK);

            // Assert
            assertThat(member.name()).hasSize(ContactDetails.MAX_NAME_LENGTH);
        }

        @Test
        void rejectsANameThatIsTooLong() {
            // Arrange
            String tooLong = "x".repeat(ContactDetails.MAX_NAME_LENGTH + 1);
            Executable act = () -> Member.create(ASSOCIATION, ContactDetails.of(tooLong, EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), CONSENT, Set.of(MemberRole.MEMBER), JOIN_CLOCK);

            // Act
            InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("100");
        }

        @Test
        void requiresAtLeastOneRole() {
            // Arrange
            Executable act = () -> Member.create(ASSOCIATION, ContactDetails.of("Ana", EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), CONSENT, Set.of(), JOIN_CLOCK);

            // Act
            InvalidMemberException ex = assertThrows(InvalidMemberException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("role");
        }

        @Test
        void requiresTheConsentRecord() {
            // Arrange
            Executable act = () -> Member.create(ASSOCIATION, ContactDetails.of("Ana", EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), null, Set.of(MemberRole.MEMBER), JOIN_CLOCK);

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
            Member member = Member.create(ASSOCIATION, ContactDetails.of("Rui", EmailAddress.of("rui@example.com"), PhoneNumber.of("912345679")), CONSENT, roles, JOIN_CLOCK);

            // Assert
            assertThat(member.hasRole(MemberRole.COACH)).isTrue();
            assertThat(member.hasRole(MemberRole.MEMBER)).isTrue();
            assertThat(member.hasRole(MemberRole.ADMIN)).isFalse();
        }
    }

    @Nested
    class Reconstruction {

        private Member rebuild(MemberStatus status, LevelId level, List<LevelChange> changes, Instant anonymisedAt) {
            return Member.reconstruct(MemberId.generate(), ASSOCIATION.id(), CONTACT, CONSENT, status, level, Set.of(MemberRole.MEMBER), changes, JOINED,
                    anonymisedAt);
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
            Executable act = () -> Member.reconstruct(MemberId.generate(), ASSOCIATION.id(), withoutPhone, CONSENT,
                    MemberStatus.ACTIVE, BEGINNER.id(), Set.of(MemberRole.MEMBER), List.of(), JOINED, null);

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

    @Nested
    class LevelChanges {

        @Test
        void recordsWhoChangedTheLevelWhenAndFromWhichToWhich() {
            // Arrange
            Member member = member();

            // Act
            Member promoted = member.changeLevel(ASSOCIATION, INTERMEDIATE.id(), COACH, LATER_CLOCK);

            // Assert
            assertThat(promoted.levelId()).isEqualTo(INTERMEDIATE.id());
            assertThat(promoted.levelChanges()).containsExactly(
                    new LevelChange(BEGINNER.id(), INTERMEDIATE.id(), COACH, LATER));
            assertThat(member.levelId()).isEqualTo(BEGINNER.id());
            assertThat(member.levelChanges()).isEmpty();
        }

        @Test
        void keepsEveryChangeInOrder() {
            // Arrange
            MemberId admin = MemberId.generate();
            Member member = member().changeLevel(ASSOCIATION, INTERMEDIATE.id(), COACH, JOIN_CLOCK);

            // Act
            Member demoted = member.changeLevel(ASSOCIATION, BEGINNER.id(), admin, LATER_CLOCK);

            // Assert
            assertThat(demoted.levelChanges()).containsExactly(
                    new LevelChange(BEGINNER.id(), INTERMEDIATE.id(), COACH, JOINED),
                    new LevelChange(INTERMEDIATE.id(), BEGINNER.id(), admin, LATER));
            assertThat(demoted.levelId()).isEqualTo(BEGINNER.id());
        }

        @Test
        void movingToTheCurrentLevelChangesNothingAndRecordsNothing() {
            // Arrange
            Member member = member();

            // Act
            Member same = member.changeLevel(ASSOCIATION, BEGINNER.id(), COACH, LATER_CLOCK);

            // Assert
            assertThat(same).isSameAs(member);
            assertThat(same.levelChanges()).isEmpty();
        }

        @Test
        void rejectsALevelOfAnotherAssociation() {
            // Arrange
            Association other = Association.create("Other", "other", null, "Porto", "x@y.co", List.of("Open"));
            Member member = member();
            Executable act = () -> member.changeLevel(other, other.entryLevelId(), COACH, LATER_CLOCK);

            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("another association");
        }

        @Test
        void rejectsALevelThatIsNotInTheAssociation() {
            // Arrange
            Member member = member();
            LevelId unknown = LevelId.generate();
            Executable act = () -> member.changeLevel(ASSOCIATION, unknown, COACH, LATER_CLOCK);

            // Act
            LevelNotFoundException ex = assertThrows(LevelNotFoundException.class, act);

            // Assert
            assertThat(ex.levelId()).isEqualTo(unknown);
        }

        @Test
        void anErasedMemberCannotChangeLevel() {
            // Arrange
            Member erased = member().anonymise(LATER_CLOCK);
            Executable act = () -> erased.changeLevel(ASSOCIATION, INTERMEDIATE.id(), COACH, LATER_CLOCK);

            // Act
            MemberAnonymisedException ex = assertThrows(MemberAnonymisedException.class, act);

            // Assert
            assertThat(ex.getMessage()).isEqualTo("An anonymised member cannot be changed");
        }

        @Test
        void aLevelChangeMustActuallyChangeTheLevel() {
            // Arrange
            Executable act = () -> new LevelChange(BEGINNER.id(), BEGINNER.id(), COACH, LATER);

            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("must change");
        }
    }

    @Nested
    class Status {

        @Test
        void deactivatesAnActiveMember() {
            // Arrange
            Member member = member();

            // Act
            Member inactive = member.deactivate();

            // Assert
            assertThat(inactive.status()).isEqualTo(MemberStatus.INACTIVE);
            assertThat(inactive.isActive()).isFalse();
            assertThat(inactive.levelChanges()).isEqualTo(member.levelChanges());
            assertThat(inactive.name()).isEqualTo(member.name());
            assertThat(member.isActive()).isTrue();
        }

        @Test
        void deactivatingAnInactiveMemberIsAnIllegalTransition() {
            // Arrange
            Member inactive = member().deactivate();
            Executable act = inactive::deactivate;

            // Act
            InvalidMemberStatusTransitionException ex = assertThrows(InvalidMemberStatusTransitionException.class, act);

            // Assert
            assertThat(ex.from()).isEqualTo(MemberStatus.INACTIVE);
            assertThat(ex.to()).isEqualTo(MemberStatus.INACTIVE);
            assertThat(ex.getMessage()).isEqualTo("Cannot move the member from inactive to inactive");
        }

        @Test
        void reactivatesAnInactiveMember() {
            // Arrange
            Member inactive = member().deactivate();

            // Act
            Member active = inactive.reactivate();

            // Assert
            assertThat(active.status()).isEqualTo(MemberStatus.ACTIVE);
        }

        @Test
        void reactivatingAnActiveMemberIsAnIllegalTransition() {
            // Arrange
            Member member = member();
            Executable act = member::reactivate;

            // Act
            InvalidMemberStatusTransitionException ex = assertThrows(InvalidMemberStatusTransitionException.class, act);

            // Assert
            assertThat(ex.from()).isEqualTo(MemberStatus.ACTIVE);
            assertThat(ex.getMessage()).isEqualTo("Cannot move the member from active to active");
        }

        @Test
        void anAnonymisedMemberCannotBeReactivated() {
            // Arrange
            Member erased = member().anonymise(LATER_CLOCK);
            Executable act = erased::reactivate;

            // Act
            MemberAnonymisedException ex = assertThrows(MemberAnonymisedException.class, act);

            // Assert
            assertThat(ex.getMessage()).isEqualTo("An anonymised member cannot be changed");
        }
    }

    @Nested
    class Roles {

        @Test
        void grantsARole() {
            // Arrange
            Member member = member();

            // Act
            Member coach = member.grantRole(MemberRole.COACH);

            // Assert
            assertThat(coach.roles()).containsExactlyInAnyOrder(MemberRole.MEMBER, MemberRole.COACH);
            assertThat(member.roles()).containsExactly(MemberRole.MEMBER);
        }

        @Test
        void revokesARole() {
            // Arrange
            Member coach = member().grantRole(MemberRole.COACH);

            // Act
            Member onlyCoach = coach.revokeRole(MemberRole.MEMBER);

            // Assert
            assertThat(onlyCoach.roles()).containsExactly(MemberRole.COACH);
        }

        @Test
        void revokingTheLastRoleIsABusinessRuleViolation() {
            // Arrange
            Member member = member();
            Executable act = () -> member.revokeRole(MemberRole.MEMBER);

            // Act
            LastRoleCannotBeRevokedException ex = assertThrows(LastRoleCannotBeRevokedException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("last role");
        }

        @Test
        void revokingARoleTheMemberDoesNotHoldChangesNothing() {
            // Arrange
            Member member = member();

            // Act
            Member same = member.revokeRole(MemberRole.ADMIN);

            // Assert
            assertThat(same.roles()).containsExactly(MemberRole.MEMBER);
        }

        @Test
        void anErasedMemberCannotGainOrLoseRoles() {
            // Arrange
            Member erased = member().anonymise(LATER_CLOCK);
            Executable grant = () -> erased.grantRole(MemberRole.ADMIN);
            Executable revoke = () -> erased.revokeRole(MemberRole.MEMBER);

            // Act
            MemberAnonymisedException grantEx = assertThrows(MemberAnonymisedException.class, grant);
            MemberAnonymisedException revokeEx = assertThrows(MemberAnonymisedException.class, revoke);

            // Assert
            assertThat(grantEx.getMessage()).contains("anonymised");
            assertThat(revokeEx.getMessage()).contains("anonymised");
        }
    }

    @Nested
    class Anonymisation {

        @Test
        void replacesContactDataWithPlaceholdersAndKeepsIdentityAndHistory() {
            // Arrange
            Member member = member().changeLevel(ASSOCIATION, INTERMEDIATE.id(), COACH, JOIN_CLOCK).grantRole(MemberRole.COACH);

            // Act
            Member erased = member.anonymise(LATER_CLOCK);

            // Assert
            assertThat(erased.id()).isEqualTo(member.id());
            assertThat(erased.associationId()).isEqualTo(member.associationId());
            assertThat(erased.name()).isEqualTo("Anonymised member");
            assertThat(erased.email().value()).isEqualTo("anonymised-" + member.id() + "@anonymised.invalid");
            assertThat(erased.phone()).isEmpty();
            assertThat(erased.levelChanges()).isEqualTo(member.levelChanges());
            assertThat(erased.levelId()).isEqualTo(INTERMEDIATE.id());
            assertThat(erased.roles()).containsExactly(MemberRole.MEMBER);
            assertThat(erased.consent()).isEqualTo(member.consent());
            assertThat(erased.joinedAt()).isEqualTo(JOINED);
        }

        @Test
        void deactivatesTheMemberAndRecordsWhen() {
            // Arrange
            Member member = member();

            // Act
            Member erased = member.anonymise(LATER_CLOCK);

            // Assert
            assertThat(erased.status()).isEqualTo(MemberStatus.INACTIVE);
            assertThat(erased.isAnonymised()).isTrue();
            assertThat(erased.anonymisedAt()).contains(LATER);
            assertThat(member.isAnonymised()).isFalse();
            assertThat(member.name()).isEqualTo("Ana Silva");
        }

        @Test
        void containsNoPersonalDataAfterwards() {
            // Arrange
            Member member = member();

            // Act
            Member erased = member.anonymise(LATER_CLOCK);

            // Assert
            assertThat(erased.name()).doesNotContain("Ana").doesNotContain("Silva");
            assertThat(erased.email().value()).doesNotContain("ana@example.com");
            assertThat(erased.phone()).isEmpty();
        }

        @Test
        void dropsAnyPrivilegedRolesOfTheErasedPerson() {
            // Arrange
            Member admin = member().grantRole(MemberRole.ADMIN).grantRole(MemberRole.COACH);

            // Act
            Member erased = admin.anonymise(LATER_CLOCK);

            // Assert
            assertThat(erased.roles()).containsExactly(MemberRole.MEMBER);
            assertThat(erased.hasRole(MemberRole.ADMIN)).isFalse();
        }

        @Test
        void rejectsAnErasureDatedBeforeTheMemberJoined() {
            // Arrange
            Member member = member();
            Clock beforeJoining = Clock.fixed(JOINED.minusSeconds(1), ZoneOffset.UTC);
            Executable act = () -> member.anonymise(beforeJoining);

            // Act
            InvalidMemberException ex = assertThrows(InvalidMemberException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("before the member joined");
        }

        @Test
        void twoErasedMembersDoNotShareAnEmail() {
            // Arrange
            Member first = member().anonymise(LATER_CLOCK);
            Member second = member().anonymise(LATER_CLOCK);

            // Act
            boolean sameEmail = first.email().equals(second.email());

            // Assert
            assertThat(sameEmail).isFalse();
        }

        @Test
        void isIdempotent() {
            // Arrange
            Member erased = member().anonymise(JOIN_CLOCK);

            // Act
            Member again = erased.anonymise(LATER_CLOCK);

            // Assert
            assertThat(again).isSameAs(erased);
            assertThat(again.anonymisedAt()).contains(JOINED);
        }

        @Test
        void anErasedMemberCanBeRebuiltFromPersistedData() {
            // Arrange
            Member erased = member().anonymise(LATER_CLOCK);

            // Act
            Member rebuilt = Member.reconstruct(erased.id(), erased.associationId(), erased.contact(), erased.consent(), erased.status(), erased.levelId(), erased.roles(),
                    erased.levelChanges(), erased.joinedAt(), erased.anonymisedAt().orElseThrow());

            // Assert
            assertThat(rebuilt.isAnonymised()).isTrue();
        }
    }

    @Nested
    class PersonalData {

        @Test
        void toStringPrintsOnlyIdentifiersAndStatus() {
            // Arrange
            Member member = member();

            // Act
            String text = member.toString();

            // Assert
            assertThat(text).contains(member.id().toString()).contains(ASSOCIATION.id().toString()).contains("ACTIVE");
            assertThat(text).doesNotContain("Ana").doesNotContain("Silva").doesNotContain("ana@example.com")
                    .doesNotContain("912345678");
        }

        @Test
        void contactValueObjectsDoNotPrintTheirContent() {
            // Arrange
            Member member = member();

            // Act
            String text = member.email() + " " + member.phone();

            // Assert
            assertThat(text).doesNotContain("ana@example.com").doesNotContain("912345678");
        }

        @Test
        void isIdentifiedByItsId() {
            // Arrange
            Member member = member();
            Member renamedCopy = member.grantRole(MemberRole.COACH);

            // Act
            boolean differentMember = member.equals(member());

            // Assert
            assertThat(member).isEqualTo(renamedCopy).hasSameHashCodeAs(renamedCopy);
            assertThat(differentMember).isFalse();
            assertThat(member).isNotEqualTo("not a member");
        }
    }
}
