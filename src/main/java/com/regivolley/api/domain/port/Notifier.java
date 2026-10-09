package com.regivolley.api.domain.port;

import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.JoinRequestId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.SessionId;

/**
 * Outbound port for telling people what happened to their bookings and requests (US-06, US-16, RN-04, RN-11). It takes
 * ids only: the adapter looks up the address and writes the message (SMTP, {@code infrastructure.notification}), so
 * no personal data travels through the application layer or its logs.
 *
 * <p>Called only after the change was committed, and a failure here never undoes it: use cases
 * swallow and log it.
 */
public interface Notifier {

    /** The member was moved from the waitlist to a confirmed seat (US-16, RN-09). */
    void bookingPromoted(AssociationId associationId, SessionId sessionId, MemberId memberId);

    /** The session was cancelled and the member's booking with it (RN-04). {@code reason} is the coach's, as written. */
    void sessionCancelled(AssociationId associationId, SessionId sessionId, MemberId memberId, String reason);

    /**
     * The member just reached the monthly no-show limit (RN-11, decided 7/10/2026: a warning, not a
     * block). The adapter warns the member and the association's administrators.
     */
    void noShowLimitReached(AssociationId associationId, MemberId memberId, int noShowsThisMonth);

    /**
     * A join request was approved and the person is now a member (US-06). The adapter writes to the address of the
     * member it looks up by id.
     */
    void memberApproved(AssociationId associationId, MemberId memberId);

    /**
     * A join request was rejected (US-06). The adapter looks the request's address up by id; the administrator's
     * free-text reason is not passed on.
     */
    void joinRequestRejected(AssociationId associationId, JoinRequestId requestId);
}
