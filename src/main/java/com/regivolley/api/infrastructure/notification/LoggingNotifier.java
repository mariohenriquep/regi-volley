package com.regivolley.api.infrastructure.notification;

import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.JoinRequestId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.port.Notifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stand-in {@link Notifier} used when no SMTP host is configured (profiles dev and test only; the SMTP adapter is {@code SmtpNotifier}): records that a notification is due,
 * with ids only - never a name, address or phone number (architecture.md section 11, NFR "Operacao").
 * The session-cancellation reason is free text typed by a coach, so it is deliberately not logged.
 */
public class LoggingNotifier implements Notifier {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingNotifier.class);

    @Override
    public void bookingPromoted(AssociationId associationId, SessionId sessionId, MemberId memberId) {
        LOG.info("Notification due: booking promoted from the waitlist (association={}, session={}, member={})",
                associationId, sessionId, memberId);
    }

    @Override
    public void sessionCancelled(AssociationId associationId, SessionId sessionId, MemberId memberId, String reason) {
        LOG.info("Notification due: session cancelled (association={}, session={}, member={})",
                associationId, sessionId, memberId);
    }

    @Override
    public void noShowLimitReached(AssociationId associationId, MemberId memberId, int noShowsThisMonth) {
        LOG.info("Notification due: no-show limit reached, to the member and the administrators "
                + "(association={}, member={}, noShows={})", associationId, memberId, noShowsThisMonth);
    }

    @Override
    public void memberApproved(AssociationId associationId, MemberId memberId) {
        LOG.info("Notification due: join request approved, welcome to the new member (association={}, member={})",
                associationId, memberId);
    }

    @Override
    public void joinRequestRejected(AssociationId associationId, JoinRequestId requestId) {
        LOG.info("Notification due: join request rejected (association={}, request={})", associationId, requestId);
    }
}
