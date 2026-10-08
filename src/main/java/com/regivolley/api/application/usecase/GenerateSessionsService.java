package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.GenerateSessionsCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.result.SessionGenerationReport;
import com.regivolley.api.domain.factory.SessionFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.SessionGenerationPolicy;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * US-10, RN-01. For each ACTIVE group of the association, creates the sessions its schedule yields in
 * the association's window starting now. Idempotent: the group is given <em>all</em> its sessions of the
 * window, CANCELLED ones included (the repository returns every status), so nothing already there -
 * cancelled or not - is created again, and a second run creates nothing.
 *
 * <p>The domain does not check the group's configuration, so this does (#16 review): a group is
 * skipped, and reported, when its coach is not a member of the association holding the COACH role or
 * when it accepts a level the association does not have. Each group is generated in its own
 * transaction, retried on a lost race against a concurrent run (the unique group+start index is the
 * backstop): the retry re-reads the sessions and finds what the winner created.
 */
@Service
public class GenerateSessionsService implements GenerateSessionsUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(GenerateSessionsService.class);

    private final AssociationRepository associations;
    private final TrainingGroupRepository groups;
    private final MemberRepository members;
    private final SessionRepository sessions;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public GenerateSessionsService(AssociationRepository associations, TrainingGroupRepository groups,
                                   MemberRepository members, SessionRepository sessions,
                                   TransactionRunner transactions, Clock clock) {
        this.associations = associations;
        this.groups = groups;
        this.members = members;
        this.sessions = sessions;
        this.unitOfWork = new UnitOfWork(transactions);
        this.clock = clock;
    }

    @Override
    public SessionGenerationReport execute(GenerateSessionsCommand command) {
        Association association = Lookups.association(associations, command.associationId());
        Instant from = clock.instant();
        int created = 0;
        List<TrainingGroupId> skipped = new ArrayList<>();
        for (TrainingGroup group : groups.findAllByAssociation(association.id())) {
            if (!group.isActive()) {
                continue;
            }
            Optional<Integer> generated = unitOfWork.retrying(() -> generateFor(association, group, from));
            if (generated.isPresent()) {
                created += generated.get();
            } else {
                skipped.add(group.id());
                LOG.warn("Group {} of association {} skipped: its coach or levels are not usable", group.id(), association.id());
            }
        }
        return new SessionGenerationReport(created, skipped);
    }

    /** The number of sessions created, or empty if the group cannot generate because of its configuration. */
    private Optional<Integer> generateFor(Association association, TrainingGroup group, Instant from) {
        if (!hasUsableCoach(association, group) || !association.hasAllLevels(group.acceptedLevels())) {
            return Optional.empty();
        }
        SessionGenerationPolicy policy = association.sessionGenerationPolicy();
        List<Session> existing = sessions.findByTrainingGroupStartingBetween(association.id(), group.id(), from,
                policy.windowEnd(from));
        List<Session> generated = SessionFactory.createSessionsFor(group, from, policy, existing);
        generated.forEach(sessions::save);
        return Optional.of(generated.size());
    }

    private boolean hasUsableCoach(Association association, TrainingGroup group) {
        return members.findById(association.id(), group.coachId())
                .filter(Member::canCoach)
                .isPresent();
    }

}
