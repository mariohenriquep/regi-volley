package com.regivolley.api.domain.model;

import com.regivolley.api.domain.exception.ConsentRequiredException;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.InvalidJoinRequestException;
import com.regivolley.api.domain.exception.InvalidJoinRequestStatusTransitionException;
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

    private static JoinRequest pending() {
        return JoinRequest.create(ASSOCIATION.id(), "  Ana Silva ", EmailAddress.of("ana@example.com"),
                PhoneNumber.of("912345678"), true, "2026-10", REQUEST_CLOCK);
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
            assertThat(request.phone()).isEqualTo(PhoneNumber.of("912345678"));
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
            Executable act = () -> JoinRequest.create(ASSOCIATION.id(), "Ana", EmailAddress.of("ana@example.com"),
                    PhoneNumber.of("912345678"), false, "2026-10", REQUEST_CLOCK);

            // Act
            ConsentRequiredException ex = assertThrows(ConsentRequiredException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("consent");
        }

        @Test
        void requiresThePolicyVersionTheConsentRefersTo() {
            // Arrange
            Executable act = () -> JoinRequest.create(ASSOCIATION.id(), "Ana", EmailAddress.of("ana@example.com"),
                    PhoneNumber.of("912345678"), true, " ", REQUEST_CLOCK);

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
            Executable act = () -> JoinRequest.create(ASSOCIATION.id(), name, EmailAddress.of("ana@example.com"),
                    PhoneNumber.of("912345678"), true, "2026-10", REQUEST_CLOCK);

            // Act
            InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

            // Assert
            assertThat(ex.field()).isEqualTo("name");
        }

        @Test
        void requiresEmailAndPhone() {
            // Arrange
            Executable noEmail = () -> JoinRequest.create(ASSOCIATION.id(), "Ana", null, PhoneNumber.of("912345678"),
                    true, "2026-10", REQUEST_CLOCK);
            Executable noPhone = () -> JoinRequest.create(ASSOCIATION.id(), "Ana", EmailAddress.of("ana@example.com"),
                    null, true, "2026-10", REQUEST_CLOCK);

            // Act
            NullPointerException email = assertThrows(NullPointerException.class, noEmail);
            NullPointerException phone = assertThrows(NullPointerException.class, noPhone);

            // Assert
            assertThat(email.getMessage()).contains("email");
            assertThat(phone.getMessage()).contains("phone");
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
            assertThat(member.name()).isEqualTo(request.name());
            assertThat(member.email()).isEqualTo(request.email());
            assertThat(member.phone()).isEqualTo(request.phone());
            assertThat(member.consent()).isEqualTo(request.consent());
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
            return JoinRequest.reconstruct(JoinRequestId.generate(), ASSOCIATION.id(), "Ana",
                    EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678"),
                    new GdprConsent(REQUESTED, "2026-10"), status, REQUESTED, decidedAt, decidedBy, reason);
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
