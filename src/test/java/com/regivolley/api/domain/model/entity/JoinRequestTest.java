package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.InvalidJoinRequestStatusTransitionException;
import com.regivolley.api.domain.factory.AssociationFactory;
import com.regivolley.api.domain.factory.JoinRequestFactory;
import com.regivolley.api.domain.model.valueobject.GdprConsent;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.JoinRequestId;
import com.regivolley.api.domain.model.valueobject.JoinRequestStatus;
import com.regivolley.api.domain.model.valueobject.MemberId;
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

    private static final Association ASSOCIATION = AssociationFactory.create("Club", "club", null, "Lisbon", "a@b.co",
            List.of("Beginner", "Intermediate"));
    private static final MemberId ADMIN = MemberId.generate();

    private static JoinRequest pending() {
        return JoinRequestFactory.create(ASSOCIATION.id(), ContactDetails.of("  Ana Silva ", EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), GdprConsent.record(true, "2026-10", REQUEST_CLOCK), REQUEST_CLOCK);
    }

    @Nested
    class Creation {
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
        void theRequestBecomesApprovedAndRecordsWhoAndWhen() {
            // Arrange
            JoinRequest request = pending();

            // Act
            JoinRequest approved = request.approve(ADMIN, DECISION_CLOCK);

            // Assert
            assertThat(approved.id()).isEqualTo(request.id());
            assertThat(approved.status()).isEqualTo(JoinRequestStatus.APPROVED);
            assertThat(approved.isPending()).isFalse();
            assertThat(approved.decidedBy()).contains(ADMIN);
            assertThat(approved.decidedAt()).contains(DECIDED);
            assertThat(approved.rejectionReason()).isEmpty();
            assertThat(request.status()).isEqualTo(JoinRequestStatus.PENDING);
        }

        @ParameterizedTest
        @EnumSource(value = JoinRequestStatus.class, names = {"APPROVED", "REJECTED"})
        void aDecidedRequestCannotBeApproved(JoinRequestStatus decided) {
            // Arrange
            JoinRequest request = decided == JoinRequestStatus.APPROVED
                    ? pending().approve(ADMIN, DECISION_CLOCK)
                    : pending().reject(ADMIN, null, DECISION_CLOCK);
            Executable act = () -> request.approve(ADMIN, DECISION_CLOCK);

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
                    ? pending().approve(ADMIN, DECISION_CLOCK)
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
            JoinRequest approved = pending().approve(ADMIN, DECISION_CLOCK);

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
            Executable approve = () -> erased.approve(ADMIN, DECISION_CLOCK);
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
