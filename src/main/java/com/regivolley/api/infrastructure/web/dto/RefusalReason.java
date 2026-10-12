package com.regivolley.api.infrastructure.web.dto;

/**
 * Why a booking, or the cancellation of one, was refused, as the wire spells it: the {@code reason} of an {@link ApiError}, stable
 * for clients to branch on (the {@code message} is for people and may be reworded). The first six are the eligibility refusals
 * of the domain ({@code BookingRejectionReason}, RN-06, RN-14, RN-18, RN-21) under their own names; the others are the window
 * (RN-03), overlap and duplicate (RN-07), the session's coach (RN-02), a session that is no longer scheduled (RN-05) and a
 * cancellation after the start (RN-10). A new value is a deliberate API change: add it to the mapper's exhaustive switch.
 */
public enum RefusalReason {
    MEMBER_INACTIVE,
    LEVEL_NOT_ALLOWED,
    NO_VALID_SUBSCRIPTION,
    PLAN_LEVEL_NOT_ALLOWED,
    NO_BALANCE,
    PAYMENT_OVERDUE,
    BOOKING_NOT_OPEN,
    BOOKING_CLOSED,
    OVERLAP,
    DUPLICATE_BOOKING,
    COACH_OF_SESSION,
    SESSION_NOT_SCHEDULED,
    CANCELLATION_CLOSED
}
