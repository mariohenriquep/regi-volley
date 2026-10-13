package com.regivolley.api.infrastructure.web.mapper;

import com.regivolley.api.domain.exception.BookingNotAllowedException;
import com.regivolley.api.domain.exception.BookingOverlapException;
import com.regivolley.api.domain.exception.BookingWindowClosedException;
import com.regivolley.api.domain.exception.BusinessRuleException;
import com.regivolley.api.domain.exception.CancellationClosedException;
import com.regivolley.api.domain.exception.CoachCannotBookOwnSessionException;
import com.regivolley.api.domain.exception.DuplicateBookingException;
import com.regivolley.api.domain.exception.SessionNotScheduledException;
import com.regivolley.api.domain.model.valueobject.BookingRejectionReason;
import com.regivolley.api.infrastructure.web.dto.RefusalReason;

import java.util.Optional;

/**
 * The machine-readable reason of a booking refusal (issue #57), taken from the domain exception that carries it and never from its
 * message. Only the refusals a member can meet when booking or cancelling have one; every other rule violation, and every
 * invariant, answers without a reason (architecture.md section 12).
 */
public final class RefusalReasonWebMapper {

    private RefusalReasonWebMapper() {
    }

    public static Optional<RefusalReason> of(BusinessRuleException refusal) {
        if (refusal instanceof BookingNotAllowedException notAllowed) {
            return Optional.of(toWire(notAllowed.reason()));
        }
        if (refusal instanceof BookingWindowClosedException window) {
            return Optional.of(switch (window.kind()) {
                case NOT_YET_OPEN -> RefusalReason.BOOKING_NOT_OPEN;
                case ALREADY_CLOSED -> RefusalReason.BOOKING_CLOSED;
            });
        }
        if (refusal instanceof BookingOverlapException) {
            return Optional.of(RefusalReason.OVERLAP);
        }
        if (refusal instanceof DuplicateBookingException) {
            return Optional.of(RefusalReason.DUPLICATE_BOOKING);
        }
        if (refusal instanceof CoachCannotBookOwnSessionException) {
            return Optional.of(RefusalReason.COACH_OF_SESSION);
        }
        if (refusal instanceof SessionNotScheduledException) {
            return Optional.of(RefusalReason.SESSION_NOT_SCHEDULED);
        }
        if (refusal instanceof CancellationClosedException) {
            return Optional.of(RefusalReason.CANCELLATION_CLOSED);
        }
        return Optional.empty();
    }

    /** Every domain reason has its wire spelling; a new one on the domain side stops the build here. */
    static RefusalReason toWire(BookingRejectionReason reason) {
        return switch (reason) {
            case MEMBER_INACTIVE -> RefusalReason.MEMBER_INACTIVE;
            case LEVEL_NOT_ALLOWED -> RefusalReason.LEVEL_NOT_ALLOWED;
            case NO_VALID_SUBSCRIPTION -> RefusalReason.NO_VALID_SUBSCRIPTION;
            case PLAN_LEVEL_NOT_ALLOWED -> RefusalReason.PLAN_LEVEL_NOT_ALLOWED;
            case NO_BALANCE -> RefusalReason.NO_BALANCE;
            case PAYMENT_OVERDUE -> RefusalReason.PAYMENT_OVERDUE;
        };
    }
}
