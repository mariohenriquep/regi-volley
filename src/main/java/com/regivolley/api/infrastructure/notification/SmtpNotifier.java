package com.regivolley.api.infrastructure.notification;

import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.JoinRequestId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.port.Notifier;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.JoinRequestRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * {@link Notifier} over SMTP (issue #40): US-06 approved / rejected, US-16 / RN-09 promoted from the waitlist, US-11 / RN-04 session
 * cancelled, RN-11 no-show warning to the member and the administrators.
 *
 * <p>The port carries ids only. Each call is queued on the {@link MailDispatcher} - so it returns at once, after the use case's
 * commit, and a slow mail server never slows a booking - and the queued task reads what it needs through the tenant-scoped
 * repositories (every read names the association), writes the message and sends it. A recipient who is gone is skipped, not retried:
 * another association's member (the id does not resolve in this tenant), a deactivated or erased member, an anonymised request.
 *
 * <p>What a message may contain: the association's name, a Lisbon date and a count - all escaped by {@link MailTemplates}, none in a
 * subject. Greetings are generic: a member's or applicant's name was typed by a visitor and the mail may go to an address that is not
 * theirs, so it is never printed, except in the warning to the administrators, who need to know who it is. The coach's cancellation
 * reason is shown (cleaned and cut); the administrator's reason for a rejection is not passed on by the port.
 *
 * <p>Logs name ids only, never an address, a name or a reason.
 */
public final class SmtpNotifier implements Notifier {

    private static final Logger LOG = LoggerFactory.getLogger(SmtpNotifier.class);

    private final MailDispatcher dispatcher;
    private final MailDelivery delivery;
    private final MailTemplates templates;
    private final MemberRepository members;
    private final SessionRepository sessions;
    private final AssociationRepository associations;
    private final JoinRequestRepository joinRequests;

    public SmtpNotifier(MailDispatcher dispatcher, MailDelivery delivery, MailTemplates templates, MemberRepository members,
                        SessionRepository sessions, AssociationRepository associations, JoinRequestRepository joinRequests) {
        this.dispatcher = dispatcher;
        this.delivery = delivery;
        this.templates = templates;
        this.members = members;
        this.sessions = sessions;
        this.associations = associations;
        this.joinRequests = joinRequests;
    }

    @Override
    public void bookingPromoted(AssociationId associationId, SessionId sessionId, MemberId memberId) {
        memberNotice("booking promoted", associationId, sessionId, memberId, NoticeKind.BOOKING_PROMOTED, facts -> Map.of(
                "association", facts.association().name(), "when", MailFormats.lisbon(facts.session().startsAt())));
    }

    @Override
    public void sessionCancelled(AssociationId associationId, SessionId sessionId, MemberId memberId, String reason) {
        String shownReason = reason == null || reason.isBlank() ? "none given" : reason;
        memberNotice("session cancelled", associationId, sessionId, memberId, NoticeKind.SESSION_CANCELLED, facts -> Map.of(
                "association", facts.association().name(), "when", MailFormats.lisbon(facts.session().startsAt()), "reason", shownReason));
    }

    @Override
    public void noShowLimitReached(AssociationId associationId, MemberId memberId, int noShowsThisMonth) {
        dispatcher.submit("no-show limit (association=" + associationId + ", member=" + memberId + ")", () -> {
            Optional<Member> member = reachable(associationId, memberId);
            Optional<Association> association = associations.findById(associationId);
            if (member.isEmpty() || association.isEmpty()) {
                skipped("no-show limit", associationId, memberId);
                return;
            }
            String count = Integer.toString(noShowsThisMonth);
            String associationName = association.get().name();
            // Everything that can fail - the lookups and the rendering - happens before the first mailbox is queued. A failure then
            // retries the whole notice with nothing sent yet, so no recipient ever receives a second copy because of another's mailbox.
            List<Queued> mails = new ArrayList<>();
            mails.add(new Queued("no-show warning to the member (association=" + associationId + ", member=" + memberId + ")",
                    member.get().email().value(),
                    templates.render(NoticeKind.NO_SHOW_MEMBER, Map.of("association", associationName, "count", count), Map.of())));
            for (Member administrator : activeAdministrators(associationId)) {
                if (!administrator.id().equals(memberId)) {
                    mails.add(new Queued("no-show warning to an administrator (association=" + associationId + ", administrator="
                            + administrator.id() + ", member=" + memberId + ")", administrator.email().value(),
                            templates.render(NoticeKind.NO_SHOW_ADMIN,
                                    Map.of("association", associationName, "member", member.get().name(), "count", count), Map.of())));
                }
            }
            // One queued task per mailbox, so a mailbox that fails is retried alone.
            mails.forEach(mail -> dispatcher.submit(mail.description(), () -> delivery.deliver(mail.recipient(), mail.content())));
        });
    }

    @Override
    public void memberApproved(AssociationId associationId, MemberId memberId) {
        memberNotice("join request approved", associationId, null, memberId, NoticeKind.JOIN_APPROVED,
                facts -> Map.of("association", facts.association().name()));
    }

    @Override
    public void joinRequestRejected(AssociationId associationId, JoinRequestId requestId) {
        dispatcher.submit("join request rejected (association=" + associationId + ", request=" + requestId + ")", () -> {
            Optional<JoinRequest> request = joinRequests.findById(associationId, requestId).filter(r -> !r.isAnonymised());
            Optional<Association> association = associations.findById(associationId);
            if (request.isEmpty() || association.isEmpty()) {
                LOG.info("Mail skipped, nobody to write to: join request rejected (association={}, request={})", associationId, requestId);
                return;
            }
            send(request.get().email().value(), NoticeKind.JOIN_REJECTED, Map.of("association", association.get().name()));
        });
    }

    /** What a notice to one member is written from. {@code session} is null for notices that are not about a session. */
    private record Facts(Member member, Association association, Session session) {
    }

    /** One message ready to hand to the mail server. */
    private record Queued(String description, String recipient, MailContent content) {
    }

    /**
     * The load-skip-send of a notice to one member: read the member (reachable only), the association and - when the notice is about
     * one - the session, in the tenant of the call; skip quietly if any is gone; otherwise write the message from them and send it.
     */
    private void memberNotice(String notice, AssociationId associationId, SessionId sessionId, MemberId memberId, NoticeKind kind,
                              Function<Facts, Map<String, String>> values) {
        String about = sessionId == null ? "" : ", session=" + sessionId;
        dispatcher.submit(notice + " (association=" + associationId + about + ", member=" + memberId + ")", () -> {
            Optional<Member> member = reachable(associationId, memberId);
            Optional<Association> association = associations.findById(associationId);
            Optional<Session> session = sessionId == null ? Optional.empty() : sessions.findById(associationId, sessionId);
            if (member.isEmpty() || association.isEmpty() || (sessionId != null && session.isEmpty())) {
                skipped(notice, associationId, memberId);
                return;
            }
            Facts facts = new Facts(member.get(), association.get(), session.orElse(null));
            send(member.get().email().value(), kind, values.apply(facts));
        });
    }

    private Optional<Member> reachable(AssociationId associationId, MemberId memberId) {
        return members.findById(associationId, memberId).filter(member -> member.isActive() && !member.isAnonymised());
    }

    private List<Member> activeAdministrators(AssociationId associationId) {
        List<MemberId> ids = members.findActiveAdminIds(associationId);
        if (ids.isEmpty()) {
            return List.of();
        }
        return members.findByIds(associationId, ids).stream().filter(member -> !member.isAnonymised()).toList();
    }

    private void send(String recipient, NoticeKind kind, Map<String, String> values) throws MessagingException {
        delivery.deliver(recipient, templates.render(kind, values, Map.of()));
    }

    private static void skipped(String notice, AssociationId associationId, MemberId memberId) {
        LOG.info("Mail skipped, nobody to write to: {} (association={}, member={})", notice, associationId, memberId);
    }
}
