package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.ConsentRequiredException;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.InvalidJoinRequestException;
import com.regivolley.api.domain.exception.InvalidJoinRequestStatusTransitionException;
import com.regivolley.api.domain.model.result.JoinRequestApproval;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.GdprConsent;
import com.regivolley.api.domain.model.valueobject.JoinRequestId;
import com.regivolley.api.domain.model.valueobject.JoinRequestStatus;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.MemberStatus;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JoinRequestTest {

    private static final Instant REQUESTED = Instant.parse("2026-10-07T20:00:00Z");
    private static final Instant DECIDED = Instant.parse("2026-10-08T09:15:00Z");
    private static final Clock REQUEST_CLOCK = Clock.fixed(REQUESTED, ZoneOffset.UTC);
    private static final Clock DECISION_CLOCK = Clock.fixed(DECIDED, ZoneOffset.UTC);

    private static final Association ASSOCIATION = Association.create("Club", "club", null, "Lisbon", "a@b.co",
            List.of("Beginner", "Intermediate"));
    private static final MemberId ADMIN = MemberId.generate();

    private static final ContactDetails CONTACT = ContactDetails.of("Ana Silva", EmailAddress.of("ana@example.com"),
            PhoneNumber.of("912345678"));

    /** A clock whose every reading is one second later than the previous one. */
    private static final class TickingClock extends Clock {
        private Instant next;

        TickingClock(Instant start) {
            this.next = start;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            Instant current = next;
            next = next.plusSeconds(1);
            return current;
        }
    }

    private static JoinRequest pending() {
        return JoinRequest.create(ASSOCIATION.id(), ContactDetails.of("  Ana Silva ", EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), true, "2026-10", REQUEST_CLOCK);
    }

    @Nested
    class Creation {

        @Test
        void startsPendingWithTheConsentStampedByTheClock() {
            // Arrange
            // (default fixtures)

            // Act
            JoinRequest request = pending();

            // Assert
            assertThat(request.id()).isNotNull();
            assertThat(request.associationId()).isEqualTo(ASSOCIATION.id());
            assertThat(request.name()).isEqualTo("Ana Silva");
            assertThat(request.email()).isEqualTo(EmailAddress.of("ana@example.com"));
            assertThat(request.phone()).contains(PhoneNumber.of("912345678"));
            assertThat(request.isAnonymised()).isFalse();
            assertThat(request.anonymisedAt()).isEmpty();
            assertThat(request.status()).isEqualTo(JoinRequestStatus.PENDING);
            assertThat(request.isPending()).isTrue();
            assertThat(request.requestedAt()).isEqualTo(REQUESTED);
            assertThat(request.consent()).isEqualTo(new GdprConsent(REQUESTED, "2026-10"));
            assertThat(request.decidedAt()).isEmpty();
            assertThat(request.decidedBy()).isEmpty();
            assertThat(request.rejectionReason()).isEmpty();
        }

        @Test
        void requiresTheRgpdConsent() {
            // Arrange
            Executable act = () -> JoinRequest.create(ASSOCIATION.id(), ContactDetails.of("Ana", EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), false, "2026-10", REQUEST_CLOCK);

            // Act
            ConsentRequiredException ex = assertThrows(ConsentRequiredException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("consent");
        }

        @Test
        void requiresThePolicyVersionTheConsentRefersTo() {
            // Arrange
            Executable act = () -> JoinRequest.create(ASSOCIATION.id(), ContactDetails.of("Ana", EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), true, " ", REQUEST_CLOCK);

            // Act
            InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

            // Assert
            assertThat(ex.field()).isEqualTo("policy version");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void requiresAName(String name) {
            // Arrange
            Executable act = () -> JoinRequest.create(ASSOCIATION.id(), ContactDetails.of(name, EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), true, "2026-10", REQUEST_CLOCK);

            // Act
            InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

            // Assert
            assertThat(ex.field()).isEqualTo("name");
        }

        @Test
        void readsTheClockOnceSoTheConsentAndTheRequestShareAnInstant() {
            // Arrange
            Clock ticking = new TickingClock(REQUESTED);

            // Act
            JoinRequest request = JoinRequest.create(ASSOCIATION.id(),
                    ContactDetails.of("Ana", EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")),
                    true, "2026-10", ticking);

            // Assert
            assertThat(request.consent().givenAt()).isEqualTo(request.requestedAt());
        }

        @Test
        void typedIdWrapsAndGenerates() {
            // Arrange
            UUID uuid = UUID.randomUUID();

            // Act
            JoinRequestId id = JoinRequestId.of(uuid);

            // Assert
            assertThat(id.value()).isEqualTo(uuid);
            assertThat(id).hasToString(uuid.toString());
            assertThat(JoinRequestId.generate()).isNotEqualTo(JoinRequestId.generate());
        }
    }

    @Nested
    class Approval {

        @Test
        void approvingCreatesAnActiveMemberAtTheEntryLevel() {
            // Arrange
            JoinRequest request = pending();

            // Act
            JoinRequestApproval approval = request.approve(ASSOCIATION, ADMIN, DECISION_CLOCK);

            // Assert
            Member member = approval.member();
            assertThat(member.associationId()).isEqualTo(ASSOCIATION.id());
            assertThat(member.levelId()).isEqualTo(ASSOCIATION.entryLevelId());
            assertThat(member.status()).isEqualTo(MemberStatus.ACTIVE);
            assertThat(member.roles()).containsExactly(MemberRole.MEMBER);
            assertThat(member.levelChanges()).isEmpty();
            assertThat(member.joinedAt()).isEqualTo(DECIDED);
        }

        @Test
        void theMemberCarriesTheRequestsContactDataAndConsent() {
            // Arrange
            JoinRequest request = pending();

            // Act
            Member member = request.approve(ASSOCIATION, ADMIN, DECISION_CLOCK).member();

            // Assert
            assertThat(member.contact()).isEqualTo(request.contact());
            assertThat(member.consent()).isEqualTo(request.consent());
        }

        @Test
        void readsTheClockOnceSoTheDecisionAndTheMembershipShareAnInstant() {
            // Arrange
            JoinRequest request = pending();
            Clock ticking = new TickingClock(DECIDED);

            // Act
            JoinRequestApproval approval = request.approve(ASSOCIATION, ADMIN, ticking);

            // Assert
            assertThat(approval.member().joinedAt()).isEqualTo(approval.request().decidedAt().orElseThrow());
        }

        @Test
        void theRequestBecomesApprovedAndRecordsWhoAndWhen() {
            // Arrange
            JoinRequest request = pending();

            // Act
            JoinRequest approved = request.approve(ASSOCIATION, ADMIN, DECISION_CLOCK).request();

            // Assert
            assertThat(approved.id()).isEqualTo(request.id());
            assertThat(approved.status()).isEqualTo(JoinRequestStatus.APPROVED);
            assertThat(approved.isPending()).isFalse();
            assertThat(approved.decidedBy()).contains(ADMIN);
            assertThat(approved.decidedAt()).contains(DECIDED);
            assertThat(approved.rejectionReason()).isEmpty();
            assertThat(request.status()).isEqualTo(JoinRequestStatus.PENDING);
        }

        @Test
        void aNewMemberStartsAtTheEntryLevelWhateverItsPosition() {
            // Arrange
            Association topEntry = ASSOCIATION.changeEntryLevel(ASSOCIATION.levels().get(1).id());
            JoinRequest request = pending();

            // Act
            Member member = request.approve(topEntry, ADMIN, DECISION_CLOCK).member();

            // Assert
            assertThat(member.levelId()).isEqualTo(topEntry.levels().get(1).id());
        }

        @Test
        void rejectsAnAssociationThatIsNotTheRequests() {
            // Arrange
            Association other = Association.create("Other", "other", null, "Porto", "x@y.co", List.of("Open"));
            JoinRequest request = pending();
            Executable act = () -> request.approve(other, ADMIN, DECISION_CLOCK);

            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("another association");
        }

        @ParameterizedTest
        @EnumSource(value = JoinRequestStatus.class, names = {"APPROVED", "REJECTED"})
        void aDecidedRequestCannotBeApproved(JoinRequestStatus decided) {
            // Arrange
            JoinRequest request = decided == JoinRequestStatus.APPROVED
                    ? pending().approve(ASSOCIATION, ADMIN, DECISION_CLOCK).request()
                    : pending().reject(ADMIN, null, DECISION_CLOCK);
            Executable act = () -> request.approve(ASSOCIATION, ADMIN, DECISION_CLOCK);

            // Act
            InvalidJoinRequestStatusTransitionException ex =
                    assertThrows(InvalidJoinRequestStatusTransitionException.class, act);

            // Assert
            assertThat(ex.from()).isEqualTo(decided);
            assertThat(ex.to()).isEqualTo(JoinRequestStatus.APPROVED);
        }
    }

    @Nested
    class Rejection {

        @Test
        void rejectsWithAReasonAndRecordsWhoAndWhen() {
            // Arrange
            JoinRequest request = pending();

            // Act
            JoinRequest rejected = request.reject(ADMIN, "  Group is full  ", DECISION_CLOCK);

            // Assert
            assertThat(rejected.status()).isEqualTo(JoinRequestStatus.REJECTED);
            assertThat(rejected.rejectionReason()).contains("Group is full");
            assertThat(rejected.decidedBy()).contains(ADMIN);
            assertThat(rejected.decidedAt()).contains(DECIDED);
            assertThat(request.isPending()).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void theReasonIsOptional(String reason) {
            // Arrange
            JoinRequest request = pending();

            // Act
            JoinRequest rejected = request.reject(ADMIN, reason, DECISION_CLOCK);

            // Assert
            assertThat(rejected.status()).isEqualTo(JoinRequestStatus.REJECTED);
            assertThat(rejected.rejectionReason()).isEmpty();
        }

        @Test
        void acceptsAReasonOfTheMaximumLength() {
            // Arrange
            JoinRequest request = pending();
            String atMax = "x".repeat(JoinRequest.MAX_REASON_LENGTH);

            // Act
            JoinRequest rejected = request.reject(ADMIN, atMax, DECISION_CLOCK);

            // Assert
            assertThat(rejected.rejectionReason().orElseThrow()).hasSize(JoinRequest.MAX_REASON_LENGTH);
        }

        @Test
        void rejectsAReasonThatIsTooLong() {
            // Arrange
            JoinRequest request = pending();
            String tooLong = "x".repeat(JoinRequest.MAX_REASON_LENGTH + 1);
            Executable act = () -> request.reject(ADMIN, tooLong, DECISION_CLOCK);

            // Act
            InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

            // Assert
            assertThat(ex.field()).isEqualTo("rejection reason");
        }

        @ParameterizedTest
        @EnumSource(value = JoinRequestStatus.class, names = {"APPROVED", "REJECTED"})
        void aDecidedRequestCannotBeRejected(JoinRequestStatus decided) {
            // Arrange
            JoinRequest request = decided == JoinRequestStatus.APPROVED
                    ? pending().approve(ASSOCIATION, ADMIN, DECISION_CLOCK).request()
                    : pending().reject(ADMIN, null, DECISION_CLOCK);
            Executable act = () -> request.reject(ADMIN, "late", DECISION_CLOCK);

            // Act
            InvalidJoinRequestStatusTransitionException ex =
                    assertThrows(InvalidJoinRequestStatusTransitionException.class, act);

            // Assert
            assertThat(ex.from()).isEqualTo(decided);
            assertThat(ex.to()).isEqualTo(JoinRequestStatus.REJECTED);
            assertThat(ex.getMessage()).startsWith("Cannot move the join request from");
        }
    }

    @Nested
    class Reconstruction {

        private JoinRequest rebuild(JoinRequestStatus status, Instant decidedAt, MemberId decidedBy, String reason) {
            return JoinRequest.reconstruct(JoinRequestId.generate(), ASSOCIATION.id(), CONTACT,
                    new GdprConsent(REQUESTED, "2026-10"), status, REQUESTED, decidedAt, decidedBy, reason, null);
        }

        @Test
        void rebuildsEachStatusConsistently() {
            // Arrange
            // (valid combinations)

            // Act
            JoinRequest pending = rebuild(JoinRequestStatus.PENDING, null, null, null);
            JoinRequest approved = rebuild(JoinRequestStatus.APPROVED, DECIDED, ADMIN, null);
            JoinRequest rejected = rebuild(JoinRequestStatus.REJECTED, DECIDED, ADMIN, "No room");

            // Assert
            assertThat(pending.isPending()).isTrue();
            assertThat(approved.decidedBy()).contains(ADMIN);
            assertThat(rejected.rejectionReason()).contains("No room");
        }

        @Test
        void aDecidedRequestNeedsADecisionTimeAndDecider() {
            // Arrange
            Executable noTime = () -> rebuild(JoinRequestStatus.APPROVED, null, ADMIN, null);
            Executable noDecider = () -> rebuild(JoinRequestStatus.REJECTED, DECIDED, null, null);

            // Act
            InvalidJoinRequestException time = assertThrows(InvalidJoinRequestException.class, noTime);
            InvalidJoinRequestException decider = assertThrows(InvalidJoinRequestException.class, noDecider);

            // Assert
            assertThat(time.getMessage()).contains("exactly when");
            assertThat(decider.getMessage()).contains("exactly when");
        }

        @Test
        void aWithdrawnRequestMayHaveNoDecider() {
            // Arrange
            // (rejected on erasure: nobody decided)

            // Act
            JoinRequest withdrawn = JoinRequest.reconstruct(JoinRequestId.generate(), ASSOCIATION.id(),
                    ContactDetails.anonymisedFor(UUID.randomUUID()), new GdprConsent(REQUESTED, "2026-10"),
                    JoinRequestStatus.REJECTED, REQUESTED, DECIDED, null, JoinRequest.WITHDRAWN_REASON, DECIDED);

            // Assert
            assertThat(withdrawn.decidedBy()).isEmpty();
            assertThat(withdrawn.isAnonymised()).isTrue();
        }

        @Test
        void anAnonymisedRequestCannotBePending() {
            // Arrange
            Executable act = () -> JoinRequest.reconstruct(JoinRequestId.generate(), ASSOCIATION.id(),
                    ContactDetails.anonymisedFor(UUID.randomUUID()), new GdprConsent(REQUESTED, "2026-10"),
                    JoinRequestStatus.PENDING, REQUESTED, null, null, null, DECIDED);

            // Act
            InvalidJoinRequestException ex = assertThrows(InvalidJoinRequestException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("pending");
        }

        @Test
        void anErasureCannotPredateTheRequest() {
            // Arrange
            Executable act = () -> JoinRequest.reconstruct(JoinRequestId.generate(), ASSOCIATION.id(),
                    ContactDetails.anonymisedFor(UUID.randomUUID()), new GdprConsent(REQUESTED, "2026-10"),
                    JoinRequestStatus.REJECTED, REQUESTED, DECIDED, null, null, REQUESTED.minusSeconds(1));

            // Act
            InvalidJoinRequestException ex = assertThrows(InvalidJoinRequestException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("before it was made");
        }

        @Test
        void aPhoneIsOnlyMissingOnAnErasedRequest() {
            // Arrange
            ContactDetails withoutPhone = ContactDetails.reconstruct("Ana", EmailAddress.of("ana@example.com"), null);
            Executable act = () -> JoinRequest.reconstruct(JoinRequestId.generate(), ASSOCIATION.id(), withoutPhone,
                    new GdprConsent(REQUESTED, "2026-10"), JoinRequestStatus.PENDING, REQUESTED, null, null, null, null);

            // Act
            InvalidJoinRequestException ex = assertThrows(InvalidJoinRequestException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("phone");
        }

        @Test
        void aPendingRequestHasNoDecision() {
            // Arrange
            Executable act = () -> rebuild(JoinRequestStatus.PENDING, DECIDED, ADMIN, null);

            // Act
            InvalidJoinRequestException ex = assertThrows(InvalidJoinRequestException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("exactly when");
        }

        @Test
        void aRequestCannotBeDecidedBeforeItWasMade() {
            // Arrange
            Executable act = () -> rebuild(JoinRequestStatus.APPROVED, REQUESTED.minusSeconds(1), ADMIN, null);

            // Act
            InvalidJoinRequestException ex = assertThrows(InvalidJoinRequestException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("before it was made");
        }

        @ParameterizedTest
        @EnumSource(value = JoinRequestStatus.class, names = {"PENDING", "APPROVED"})
        void onlyARejectedRequestHasAReason(JoinRequestStatus status) {
            // Arrange
            Instant decidedAt = status == JoinRequestStatus.PENDING ? null : DECIDED;
            MemberId decidedBy = status == JoinRequestStatus.PENDING ? null : ADMIN;
            Executable act = () -> rebuild(status, decidedAt, decidedBy, "reason");

            // Act
            InvalidJoinRequestException ex = assertThrows(InvalidJoinRequestException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("rejected");
        }
    }

    @Nested
    class Anonymisation {

        private static final Instant ERASED = Instant.parse("2026-11-01T12:00:00Z");
        private final Clock erasureClock = Clock.fixed(ERASED, ZoneOffset.UTC);

        @Test
        void aPendingRequestIsWithdrawnAndLosesItsPersonalData() {
            // Arrange
            JoinRequest request = pending();

            // Act
            JoinRequest erased = request.anonymise(erasureClock);

            // Assert
            assertThat(erased.id()).isEqualTo(request.id());
            assertThat(erased.associationId()).isEqualTo(request.associationId());
            assertThat(erased.name()).isEqualTo(ContactDetails.ANONYMISED_NAME);
            assertThat(erased.email().value()).isEqualTo("anonymised-" + request.id() + "@anonymised.invalid");
            assertThat(erased.phone()).isEmpty();
            assertThat(erased.consent()).isEqualTo(request.consent());
            assertThat(erased.requestedAt()).isEqualTo(REQUESTED);
            assertThat(erased.status()).isEqualTo(JoinRequestStatus.REJECTED);
            assertThat(erased.rejectionReason()).contains("Withdrawn on erasure request");
            assertThat(erased.decidedAt()).contains(ERASED);
            assertThat(erased.decidedBy()).isEmpty();
            assertThat(erased.anonymisedAt()).contains(ERASED);
            assertThat(erased.isAnonymised()).isTrue();
            assertThat(request.isPending()).isTrue();
        }

        @Test
        void anApprovedRequestKeepsItsStatusAndDecisionAudit() {
            // Arrange
            JoinRequest approved = pending().approve(ASSOCIATION, ADMIN, DECISION_CLOCK).request();

            // Act
            JoinRequest erased = approved.anonymise(erasureClock);

            // Assert
            assertThat(erased.status()).isEqualTo(JoinRequestStatus.APPROVED);
            assertThat(erased.decidedBy()).contains(ADMIN);
            assertThat(erased.decidedAt()).contains(DECIDED);
            assertThat(erased.name()).isEqualTo(ContactDetails.ANONYMISED_NAME);
            assertThat(erased.anonymisedAt()).contains(ERASED);
        }

        @Test
        void aRejectedRequestKeepsItsDecisionButDropsTheFreeTextReason() {
            // Arrange
            JoinRequest rejected = pending().reject(ADMIN, "Ana's number is wrong", DECISION_CLOCK);

            // Act
            JoinRequest erased = rejected.anonymise(erasureClock);

            // Assert
            assertThat(erased.status()).isEqualTo(JoinRequestStatus.REJECTED);
            assertThat(erased.decidedBy()).contains(ADMIN);
            assertThat(erased.decidedAt()).contains(DECIDED);
            assertThat(erased.rejectionReason()).isEmpty();
        }

        @Test
        void isIdempotent() {
            // Arrange
            JoinRequest erased = pending().anonymise(DECISION_CLOCK);

            // Act
            JoinRequest again = erased.anonymise(erasureClock);

            // Assert
            assertThat(again).isSameAs(erased);
            assertThat(again.anonymisedAt()).contains(DECIDED);
        }

        @Test
        void anErasedRequestCanNoLongerBeDecided() {
            // Arrange
            JoinRequest erased = pending().anonymise(erasureClock);
            Executable approve = () -> erased.approve(ASSOCIATION, ADMIN, DECISION_CLOCK);
            Executable reject = () -> erased.reject(ADMIN, null, DECISION_CLOCK);

            // Act
            InvalidJoinRequestStatusTransitionException approveEx =
                    assertThrows(InvalidJoinRequestStatusTransitionException.class, approve);
            InvalidJoinRequestStatusTransitionException rejectEx =
                    assertThrows(InvalidJoinRequestStatusTransitionException.class, reject);

            // Assert
            assertThat(approveEx.from()).isEqualTo(JoinRequestStatus.REJECTED);
            assertThat(rejectEx.from()).isEqualTo(JoinRequestStatus.REJECTED);
        }

        @Test
        void toStringOfAnErasedRequestStillHasNoPersonalData() {
            // Arrange
            JoinRequest erased = pending().anonymise(erasureClock);

            // Act
            String text = erased.toString();

            // Assert
            assertThat(text).contains(erased.id().toString()).doesNotContain("Ana").doesNotContain("ana@example.com");
        }
    }

    @Nested
    class PersonalData {

        @Test
        void toStringPrintsOnlyIdentifiersAndStatus() {
            // Arrange
            JoinRequest request = pending();

            // Act
            String text = request.toString();

            // Assert
            assertThat(text).contains(request.id().toString()).contains("PENDING");
            assertThat(text).doesNotContain("Ana").doesNotContain("Silva").doesNotContain("ana@example.com")
                    .doesNotContain("912345678");
        }

        @Test
        void isIdentifiedByItsId() {
            // Arrange
            JoinRequest request = pending();
            JoinRequest rejected = request.reject(ADMIN, null, DECISION_CLOCK);

            // Act
            boolean differentRequest = request.equals(pending());

            // Assert
            assertThat(request).isEqualTo(rejected).hasSameHashCodeAs(rejected);
            assertThat(differentRequest).isFalse();
            assertThat(request).isNotEqualTo("not a request");
        }
    }
}
