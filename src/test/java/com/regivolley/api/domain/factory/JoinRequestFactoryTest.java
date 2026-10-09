package com.regivolley.api.domain.factory;

import com.regivolley.api.domain.exception.ConsentRequiredException;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.InvalidJoinRequestException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.GdprConsent;
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

class JoinRequestFactoryTest {

    private static final Instant REQUESTED = Instant.parse("2026-10-07T20:00:00Z");

    private static final Instant DECIDED = Instant.parse("2026-10-08T09:15:00Z");

    private static final Clock REQUEST_CLOCK = Clock.fixed(REQUESTED, ZoneOffset.UTC);

    private static final Clock DECISION_CLOCK = Clock.fixed(DECIDED, ZoneOffset.UTC);

    private static final Association ASSOCIATION = AssociationFactory.create("Club", "club", null, "Lisbon", "a@b.co",
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
        return JoinRequestFactory.create(ASSOCIATION.id(), ContactDetails.of("  Ana Silva ", EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), GdprConsent.record(true, "2026-10", REQUEST_CLOCK), REQUEST_CLOCK);
    }

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
        Executable act = () -> JoinRequestFactory.create(ASSOCIATION.id(), ContactDetails.of("Ana", EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), GdprConsent.record(false, "2026-10", REQUEST_CLOCK), REQUEST_CLOCK);

        // Act
        ConsentRequiredException ex = assertThrows(ConsentRequiredException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("consent");
    }

    @Test
    void requiresThePolicyVersionTheConsentRefersTo() {
        // Arrange
        Executable act = () -> JoinRequestFactory.create(ASSOCIATION.id(), ContactDetails.of("Ana", EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), GdprConsent.record(true, " ", REQUEST_CLOCK), REQUEST_CLOCK);

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
        Executable act = () -> JoinRequestFactory.create(ASSOCIATION.id(), ContactDetails.of(name, EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), GdprConsent.record(true, "2026-10", REQUEST_CLOCK), REQUEST_CLOCK);

        // Act
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

        // Assert
        assertThat(ex.field()).isEqualTo("name");
    }

    @Test
    void keepsTheConsentTheCallerRecordedAndStampsTheRequestWithTheClock() {
        // Arrange
        GdprConsent consent = new GdprConsent(REQUESTED.minusSeconds(5), "2026-10");

        // Act
        JoinRequest request = JoinRequestFactory.create(ASSOCIATION.id(), ContactDetails.of("Ana", EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")), consent, REQUEST_CLOCK);

        // Assert
        assertThat(request.consent()).isEqualTo(consent);
        assertThat(request.requestedAt()).isEqualTo(REQUESTED);
    }

    @Nested
    class Reconstruction {

        @Test
        void keepsTheVersionItWasLoadedWithThroughEveryDecisionAndErasure() {
            // Arrange
            JoinRequest loaded = JoinRequestFactory.reconstitute(JoinRequestId.generate(), ASSOCIATION.id(), CONTACT,
                    new GdprConsent(REQUESTED, "2026-10"), JoinRequestStatus.PENDING, REQUESTED, null, null, null, null, 9L);
            Clock clock = DECISION_CLOCK;

            // Act
            JoinRequest rejected = loaded.reject(ADMIN, "Full", clock);
            JoinRequest approved = loaded.approve(ADMIN, clock);
            JoinRequest erased = loaded.anonymise(clock);

            // Assert
            assertThat(loaded.version()).isEqualTo(9L);
            assertThat(rejected.version()).isEqualTo(9L);
            assertThat(approved.version()).isEqualTo(9L);
            assertThat(erased.version()).isEqualTo(9L);
        }

        @Test
        void aNewRequestStartsAtVersionZeroAndANegativeVersionIsRejected() {
            // Arrange
            Executable negative = () -> JoinRequestFactory.reconstitute(JoinRequestId.generate(), ASSOCIATION.id(), CONTACT,
                    new GdprConsent(REQUESTED, "2026-10"), JoinRequestStatus.PENDING, REQUESTED, null, null, null, null, -1L);

            // Act
            JoinRequest created = JoinRequestFactory.create(ASSOCIATION.id(), CONTACT, GdprConsent.record(true, "2026-10", REQUEST_CLOCK), REQUEST_CLOCK);
            InvalidJoinRequestException ex = assertThrows(InvalidJoinRequestException.class, negative);

            // Assert
            assertThat(created.version()).isZero();
            assertThat(ex.getMessage()).contains("version");
        }

        private JoinRequest rebuild(JoinRequestStatus status, Instant decidedAt, MemberId decidedBy, String reason) {
            return JoinRequestFactory.reconstitute(JoinRequestId.generate(), ASSOCIATION.id(), CONTACT,
                    new GdprConsent(REQUESTED, "2026-10"), status, REQUESTED, decidedAt, decidedBy, reason, null, 0L);
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
            JoinRequest withdrawn = JoinRequestFactory.reconstitute(JoinRequestId.generate(), ASSOCIATION.id(),
                    ContactDetails.anonymisedFor(UUID.randomUUID()), new GdprConsent(REQUESTED, "2026-10"),
                    JoinRequestStatus.REJECTED, REQUESTED, DECIDED, null, JoinRequest.WITHDRAWN_REASON, DECIDED, 0L);

            // Assert
            assertThat(withdrawn.decidedBy()).isEmpty();
            assertThat(withdrawn.isAnonymised()).isTrue();
        }

        @Test
        void anAnonymisedRequestCannotBePending() {
            // Arrange
            Executable act = () -> JoinRequestFactory.reconstitute(JoinRequestId.generate(), ASSOCIATION.id(),
                    ContactDetails.anonymisedFor(UUID.randomUUID()), new GdprConsent(REQUESTED, "2026-10"),
                    JoinRequestStatus.PENDING, REQUESTED, null, null, null, DECIDED, 0L);

            // Act
            InvalidJoinRequestException ex = assertThrows(InvalidJoinRequestException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("pending");
        }

        @Test
        void anErasureCannotPredateTheRequest() {
            // Arrange
            Executable act = () -> JoinRequestFactory.reconstitute(JoinRequestId.generate(), ASSOCIATION.id(),
                    ContactDetails.anonymisedFor(UUID.randomUUID()), new GdprConsent(REQUESTED, "2026-10"),
                    JoinRequestStatus.REJECTED, REQUESTED, DECIDED, null, null, REQUESTED.minusSeconds(1), 0L);

            // Act
            InvalidJoinRequestException ex = assertThrows(InvalidJoinRequestException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("before it was made");
        }

        @Test
        void aPhoneIsOnlyMissingOnAnErasedRequest() {
            // Arrange
            ContactDetails withoutPhone = ContactDetails.reconstruct("Ana", EmailAddress.of("ana@example.com"), null);
            Executable act = () -> JoinRequestFactory.reconstitute(JoinRequestId.generate(), ASSOCIATION.id(), withoutPhone,
                    new GdprConsent(REQUESTED, "2026-10"), JoinRequestStatus.PENDING, REQUESTED, null, null, null, null, 0L);

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
}
