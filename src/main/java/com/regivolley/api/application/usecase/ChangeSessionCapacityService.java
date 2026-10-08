package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ChangeSessionCapacityCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.result.CapacityChanged;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.result.CapacityChange;
import com.regivolley.api.domain.port.Notifier;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * US-12, RN-02, RN-09. The coach of the session or an administrator changes its capacity. The
 * aggregate refuses to go below the confirmed bookings and promotes the waitlist into new seats; this
 * saves the session, charges each promoted member and notifies them after the commit.
 */
@Service
public class ChangeSessionCapacityService implements ChangeSessionCapacityUseCase {

    private final SessionRepository sessions;
    private final MemberRepository members;
    private final AssociationRepository associations;
    private final SubscriptionRepository subscriptions;
    private final SeatPromoter promoter;
    private final Notifier notifier;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ChangeSessionCapacityService(SessionRepository sessions, MemberRepository members,
                                        AssociationRepository associations, TrainingGroupRepository groups,
                                        SubscriptionRepository subscriptions, TransactionRunner transactions,
                                        Notifier notifier, Clock clock) {
        this.sessions = sessions;
        this.members = members;
        this.associations = associations;
        this.subscriptions = subscriptions;
        this.promoter = new SeatPromoter(members, subscriptions, groups);
        this.notifier = notifier;
        this.unitOfWork = new UnitOfWork(transactions);
        this.clock = clock;
    }

    @Override
    public CapacityChanged execute(ChangeSessionCapacityCommand command) {
        return unitOfWork.retryingAndNotify(() -> attempt(command), notifier);
    }

    private Outcome<CapacityChanged> attempt(ChangeSessionCapacityCommand command) {
        var associationId = command.actor().associationId();
        Member actor = Lookups.member(members, associationId, command.actor().memberId());
        Session session = Lookups.session(sessions, associationId, command.sessionId());
        Permissions.requireStaffOf(actor, session, "change this session's capacity");
        Association association = Lookups.association(associations, associationId);

        PromotionPlan plan = promoter.planFor(association, session);
        CapacityChange change = session.changeCapacity(command.capacity(), clock, plan.eligibility());
        Session stored = sessions.save(change.session());
        plan.charge(change.promoted()).forEach(subscriptions::save);

        List<Consumer<Notifier>> notifications = new ArrayList<>();
        change.promoted().forEach(promoted -> notifications.add(
                n -> n.bookingPromoted(associationId, stored.id(), promoted.memberId())));
        return Outcome.of(new CapacityChanged(stored, change.promoted()), notifications);
    }
}
