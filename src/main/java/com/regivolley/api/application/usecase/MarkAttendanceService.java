package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.AttendanceEntry;
import com.regivolley.api.application.command.AttendanceMark;
import com.regivolley.api.application.command.MarkAttendanceCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.result.AttendanceMarked;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.port.Notifier;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * US-17, RN-11. The coach of the session or an administrator marks each booking ATTENDED or NO_SHOW,
 * all together or not at all; the aggregate only lets that happen from the start of the session on.
 *
 * <p>RN-11, decided 7/10/2026: when a member's no-show count in the (Lisbon) month reaches the
 * association's limit - 3 unless configured - the member and the administrators are warned, once, at
 * exactly the limit. It never blocks a booking.
 *
 * <p><b>Known miss (warning only):</b> the count is read after this attempt's save but within its own
 * transaction, so two coaches or administrators marking different no-shows of the same member in
 * different sessions at the same moment can each see a count one short of the other's and the warning
 * at the limit is skipped (or, rarely, sent twice). Nothing is blocked or lost - the no-shows are stored
 * correctly and the next one is counted right - so, given that RN-11 is a warning, this is accepted
 * rather than serialising attendance marking per member.
 */
@Service
public class MarkAttendanceService implements MarkAttendanceUseCase {

    private final SessionRepository sessions;
    private final MemberRepository members;
    private final AssociationRepository associations;
    private final NoShowCounter noShows;
    private final Notifier notifier;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public MarkAttendanceService(SessionRepository sessions, MemberRepository members,
                                 AssociationRepository associations, TransactionRunner transactions,
                                 Notifier notifier, Clock clock) {
        this.sessions = sessions;
        this.members = members;
        this.associations = associations;
        this.noShows = new NoShowCounter(sessions);
        this.notifier = notifier;
        this.unitOfWork = new UnitOfWork(transactions);
        this.clock = clock;
    }

    @Override
    public AttendanceMarked execute(MarkAttendanceCommand command) {
        return unitOfWork.retryingAndNotify(() -> attempt(command), notifier);
    }

    private Outcome<AttendanceMarked> attempt(MarkAttendanceCommand command) {
        var associationId = command.actor().associationId();
        Member actor = Lookups.member(members, associationId, command.actor().memberId());
        Session session = Lookups.session(sessions, associationId, command.sessionId());
        Permissions.requireStaffOf(actor, session, "mark attendance in this session");
        Association association = Lookups.association(associations, associationId);

        Session marked = session;
        List<MemberId> absent = new ArrayList<>();
        for (AttendanceEntry entry : command.entries()) {
            if (entry.mark() == AttendanceMark.NO_SHOW) {
                marked = marked.markNoShow(entry.bookingId(), clock);
                absent.add(memberOf(marked, entry.bookingId()));
            } else {
                marked = marked.markAttended(entry.bookingId(), clock);
            }
        }
        Session stored = sessions.save(marked);

        List<MemberId> warned = new ArrayList<>();
        List<Consumer<Notifier>> notifications = new ArrayList<>();
        for (MemberId memberId : absent) {
            int count = noShows.inMonthOf(associationId, memberId, stored.startsAt());
            if (association.noShowPolicy().isReachedBy(count)) {
                warned.add(memberId);
                notifications.add(n -> n.noShowLimitReached(associationId, memberId, count));
            }
        }
        return Outcome.of(new AttendanceMarked(stored, warned), notifications);
    }

    private static MemberId memberOf(Session session, BookingId bookingId) {
        return session.findBooking(bookingId).map(Booking::memberId).orElseThrow();
    }
}
