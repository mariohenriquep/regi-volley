package com.regivolley.api.domain.model;

import com.regivolley.api.domain.exception.InvalidBookingException;
import com.regivolley.api.domain.exception.InvalidBookingStatusTransitionException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingTest {

    private static final AssociationId ASSOCIATION = AssociationId.generate();
    private static final SessionId SESSION = SessionId.generate();
    private static final MemberId MEMBER = MemberId.generate();
    private static final Instant REQUESTED_AT = Instant.parse("2026-10-14T10:00:00Z");

    private static final Instant CONFIRMED_AT = Instant.parse("2026-10-14T10:05:00Z");

    private static Booking bookingIn(BookingStatus status) {
        CancellationKind kind = status == BookingStatus.CANCELLED ? CancellationKind.FREE : null;
        Instant confirmedAt = status == BookingStatus.WAITLISTED || status == BookingStatus.CANCELLED ? null : CONFIRMED_AT;
        return Booking.reconstruct(BookingId.generate(), ASSOCIATION, SESSION, MEMBER, status, REQUESTED_AT,
                confirmedAt, kind);
    }

    private static Booking reconstruct(BookingStatus status, Instant confirmedAt, CancellationKind kind) {
        return Booking.reconstruct(BookingId.generate(), ASSOCIATION, SESSION, MEMBER, status, REQUESTED_AT,
                confirmedAt, kind);
    }

    private static Booking applyTransition(Booking booking, BookingStatus target) {
        return switch (target) {
            case CONFIRMED -> booking.confirm(CONFIRMED_AT);
            case ATTENDED -> booking.markAttended();
            case NO_SHOW -> booking.markNoShow();
            case CANCELLED -> booking.cancel(CancellationKind.FREE);
            case WAITLISTED -> throw new IllegalArgumentException("WAITLISTED is only an initial status");
        };
    }

    @Nested
    class Creation {

        @ParameterizedTest
        @EnumSource(value = BookingStatus.class, names = {"WAITLISTED", "CONFIRMED"})
        void createsWithAnAllowedInitialStatus(BookingStatus initial) {
            // Arrange
            // (initial status provided by the parameter)

            // Act
            Booking booking = Booking.create(ASSOCIATION, SESSION, MEMBER, initial, REQUESTED_AT);

            // Assert
            assertThat(booking.id()).isNotNull();
            assertThat(booking.associationId()).isEqualTo(ASSOCIATION);
            assertThat(booking.sessionId()).isEqualTo(SESSION);
            assertThat(booking.memberId()).isEqualTo(MEMBER);
            assertThat(booking.status()).isEqualTo(initial);
            assertThat(booking.requestedAt()).isEqualTo(REQUESTED_AT);
            assertThat(booking.cancellationKind()).isEmpty();
            assertThat(booking.isActive()).isTrue();
        }

        @Test
        void aBookingCreatedConfirmedRecordsTheConfirmationAtTheRequestInstant() {
            // Arrange
            // (no input)

            // Act
            Booking booking = Booking.create(ASSOCIATION, SESSION, MEMBER, BookingStatus.CONFIRMED, REQUESTED_AT);

            // Assert
            assertThat(booking.confirmedAt()).contains(REQUESTED_AT);
            assertThat(booking.consumedCredit()).isTrue();
        }

        @Test
        void aWaitlistedBookingHasNotBeenConfirmed() {
            // Arrange
            // (no input)

            // Act
            Booking booking = Booking.create(ASSOCIATION, SESSION, MEMBER, BookingStatus.WAITLISTED, REQUESTED_AT);

            // Assert
            assertThat(booking.confirmedAt()).isEmpty();
            assertThat(booking.consumedCredit()).isFalse();
        }

        @ParameterizedTest(name = "{0} with confirmedAt={1}")
        @MethodSource("inconsistentConfirmations")
        void rejectsAConfirmationInstantInconsistentWithTheStatus(BookingStatus status, Instant confirmedAt,
                                                                  CancellationKind kind) {
            // Arrange
            Executable act = () -> reconstruct(status, confirmedAt, kind);

            // Act
            InvalidBookingException ex = assertThrows(InvalidBookingException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("confirmed");
        }

        static Stream<Arguments> inconsistentConfirmations() {
            return Stream.of(
                    Arguments.of(BookingStatus.WAITLISTED, CONFIRMED_AT, null),
                    Arguments.of(BookingStatus.CONFIRMED, null, null),
                    Arguments.of(BookingStatus.ATTENDED, null, null),
                    Arguments.of(BookingStatus.NO_SHOW, null, null),
                    Arguments.of(BookingStatus.CANCELLED, null, CancellationKind.LATE),
                    Arguments.of(BookingStatus.CANCELLED, CONFIRMED_AT, CancellationKind.NOT_PROMOTED),
                    Arguments.of(BookingStatus.CONFIRMED, REQUESTED_AT.minusSeconds(1), null)
            );
        }

        @ParameterizedTest
        @EnumSource(value = BookingStatus.class, names = {"ATTENDED", "NO_SHOW", "CANCELLED"})
        void rejectsANonInitialStatus(BookingStatus initial) {
            // Arrange
            Executable act = () -> Booking.create(ASSOCIATION, SESSION, MEMBER, initial, REQUESTED_AT);

            // Act
            InvalidBookingException ex = assertThrows(InvalidBookingException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains(initial.name());
        }

        @Test
        void rejectsCancelledWithoutCancellationKind() {
            // Arrange
            Executable act = () -> Booking.reconstruct(BookingId.generate(), ASSOCIATION, SESSION, MEMBER,
                    BookingStatus.CANCELLED, REQUESTED_AT, null, null);

            // Act
            InvalidBookingException ex = assertThrows(InvalidBookingException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("cancellation kind");
        }

        @Test
        void rejectsCancellationKindOnANonCancelledBooking() {
            // Arrange
            Executable act = () -> Booking.reconstruct(BookingId.generate(), ASSOCIATION, SESSION, MEMBER,
                    BookingStatus.CONFIRMED, REQUESTED_AT, CONFIRMED_AT, CancellationKind.FREE);

            // Act
            InvalidBookingException ex = assertThrows(InvalidBookingException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("cancellation kind");
        }

        @Test
        void rejectsNullRequiredFields() {
            // Arrange
            Executable act = () -> Booking.reconstruct(null, ASSOCIATION, SESSION, MEMBER,
                    BookingStatus.CONFIRMED, REQUESTED_AT, CONFIRMED_AT, null);

            // Act
            NullPointerException ex = assertThrows(NullPointerException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("id");
        }
    }

    @Nested
    class Transitions {

        private static final BookingStatus[] TARGETS = {BookingStatus.CONFIRMED, BookingStatus.ATTENDED,
                BookingStatus.NO_SHOW, BookingStatus.CANCELLED};

        private static boolean isLegal(BookingStatus from, BookingStatus to) {
            return switch (from) {
                case WAITLISTED -> to == BookingStatus.CONFIRMED || to == BookingStatus.CANCELLED;
                case CONFIRMED -> to == BookingStatus.ATTENDED || to == BookingStatus.NO_SHOW
                        || to == BookingStatus.CANCELLED;
                case ATTENDED, NO_SHOW, CANCELLED -> false;
            };
        }

        private static Stream<Arguments> transitions(boolean legal) {
            Stream.Builder<Arguments> all = Stream.builder();
            for (BookingStatus from : BookingStatus.values()) {
                for (BookingStatus to : TARGETS) {
                    if (isLegal(from, to) == legal) {
                        all.add(Arguments.of(from, to));
                    }
                }
            }
            return all.build();
        }

        static Stream<Arguments> legalTransitions() {
            return transitions(true);
        }

        static Stream<Arguments> illegalTransitions() {
            return transitions(false);
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @MethodSource("legalTransitions")
        void allowsTheLegalRn12Transitions(BookingStatus from, BookingStatus to) {
            // Arrange
            Booking booking = bookingIn(from);

            // Act
            Booking moved = applyTransition(booking, to);

            // Assert
            assertThat(moved.status()).isEqualTo(to);
            assertThat(moved.id()).isEqualTo(booking.id());
            assertThat(moved.requestedAt()).isEqualTo(REQUESTED_AT);
            assertThat(booking.status()).isEqualTo(from);
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @MethodSource("illegalTransitions")
        void rejectsTheIllegalRn12Transitions(BookingStatus from, BookingStatus to) {
            // Arrange
            Booking booking = bookingIn(from);
            Executable act = () -> applyTransition(booking, to);

            // Act
            InvalidBookingStatusTransitionException ex =
                    assertThrows(InvalidBookingStatusTransitionException.class, act);

            // Assert
            assertThat(ex.from()).isEqualTo(from);
            assertThat(ex.to()).isEqualTo(to);
            assertThat(ex.getMessage()).startsWith("Cannot move the booking");
        }

        @Test
        void confirmingRecordsWhenTheSeatWasAssigned() {
            // Arrange
            Booking waitlisted = bookingIn(BookingStatus.WAITLISTED);

            // Act
            Booking confirmed = waitlisted.confirm(CONFIRMED_AT);

            // Assert
            assertThat(confirmed.confirmedAt()).contains(CONFIRMED_AT);
            assertThat(confirmed.consumedCredit()).isTrue();
            assertThat(waitlisted.confirmedAt()).isEmpty();
        }

        @Test
        void keepsTheConfirmationInstantThroughAttendanceAndCancellation() {
            // Arrange
            Booking confirmed = bookingIn(BookingStatus.CONFIRMED);

            // Act
            Booking attended = confirmed.markAttended();
            Booking cancelled = confirmed.cancel(CancellationKind.LATE);

            // Assert
            assertThat(attended.confirmedAt()).contains(CONFIRMED_AT);
            assertThat(cancelled.confirmedAt()).contains(CONFIRMED_AT);
        }

        @Test
        void rejectsANullConfirmationInstant() {
            // Arrange
            Booking waitlisted = bookingIn(BookingStatus.WAITLISTED);
            Executable act = () -> waitlisted.confirm(null);

            // Act
            NullPointerException ex = assertThrows(NullPointerException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("at");
        }

        @ParameterizedTest
        @EnumSource(value = CancellationKind.class, names = {"FREE", "LATE", "BY_SESSION"})
        void recordsTheCancellationKindOfAConfirmedBooking(CancellationKind kind) {
            // Arrange
            Booking booking = bookingIn(BookingStatus.CONFIRMED);

            // Act
            Booking cancelled = booking.cancel(kind);

            // Assert
            assertThat(cancelled.cancellationKind()).contains(kind);
            assertThat(cancelled.isActive()).isFalse();
        }

        @Test
        void recordsNotPromotedForAWaitlistedBooking() {
            // Arrange
            Booking booking = bookingIn(BookingStatus.WAITLISTED);

            // Act
            Booking cancelled = booking.cancel(CancellationKind.NOT_PROMOTED);

            // Assert
            assertThat(cancelled.cancellationKind()).contains(CancellationKind.NOT_PROMOTED);
            assertThat(cancelled.consumedCredit()).isFalse();
        }

        @Test
        void rejectsNullCancellationKind() {
            // Arrange
            Booking booking = bookingIn(BookingStatus.CONFIRMED);
            Executable act = () -> booking.cancel(null);

            // Act
            NullPointerException ex = assertThrows(NullPointerException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("kind");
        }

        @ParameterizedTest
        @EnumSource(BookingStatus.class)
        void isActiveOnlyWhileWaitlistedOrConfirmed(BookingStatus status) {
            // Arrange
            Booking booking = bookingIn(status);

            // Act
            boolean active = booking.isActive();

            // Assert
            assertThat(active).isEqualTo(status == BookingStatus.WAITLISTED || status == BookingStatus.CONFIRMED);
        }
    }

    @Nested
    class Credit {

        static Stream<Arguments> cases() {
            return Stream.of(
                    // status, confirmedAt, kind, consumed, refundable, bySession
                    Arguments.of(BookingStatus.WAITLISTED, null, null, false, false, false),
                    Arguments.of(BookingStatus.CONFIRMED, CONFIRMED_AT, null, true, false, false),
                    Arguments.of(BookingStatus.ATTENDED, CONFIRMED_AT, null, true, false, false),
                    Arguments.of(BookingStatus.CANCELLED, CONFIRMED_AT, CancellationKind.FREE, true, true, false),
                    Arguments.of(BookingStatus.CANCELLED, CONFIRMED_AT, CancellationKind.LATE, true, false, false),
                    Arguments.of(BookingStatus.CANCELLED, CONFIRMED_AT, CancellationKind.BY_SESSION, true, true, true),
                    Arguments.of(BookingStatus.CANCELLED, null, CancellationKind.FREE, false, false, false),
                    Arguments.of(BookingStatus.CANCELLED, null, CancellationKind.BY_SESSION, false, false, true),
                    Arguments.of(BookingStatus.CANCELLED, null, CancellationKind.NOT_PROMOTED, false, false, false)
            );
        }

        @ParameterizedTest
        @MethodSource("cases")
        void tracksWhetherACreditWasConsumedAndIsRefundable(BookingStatus status, Instant confirmedAt,
                                                            CancellationKind kind, boolean consumed,
                                                            boolean refundable, boolean bySession) {
            // Arrange
            Booking booking = reconstruct(status, confirmedAt, kind);

            // Act
            boolean actualConsumed = booking.consumedCredit();
            boolean actualRefundable = booking.creditRefundable();
            boolean actualBySession = booking.isCancelledBySession();

            // Assert
            assertThat(actualConsumed).isEqualTo(consumed);
            assertThat(actualRefundable).isEqualTo(refundable);
            assertThat(actualBySession).isEqualTo(bySession);
        }
    }

    @Nested
    class Identity {

        @Test
        void equalsAndHashCodeUseTheId() {
            // Arrange
            Booking booking = bookingIn(BookingStatus.CONFIRMED);
            Booking sameIdOtherState = booking.markAttended();
            Booking other = bookingIn(BookingStatus.CONFIRMED);

            // Act
            boolean sameId = booking.equals(sameIdOtherState);
            boolean differentId = booking.equals(other);

            // Assert
            assertThat(sameId).isTrue();
            assertThat(booking).hasSameHashCodeAs(sameIdOtherState);
            assertThat(differentId).isFalse();
            assertThat(booking).isNotEqualTo("not a booking");
            assertThat(booking).isEqualTo(booking);
            assertThat(booking.toString()).contains("CONFIRMED");
        }
    }
}
