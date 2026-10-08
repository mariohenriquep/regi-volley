package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.CreateTrainingGroupCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.domain.exception.VenueNotFoundException;
import com.regivolley.api.domain.factory.TrainingGroupFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import com.regivolley.api.domain.repository.VenueRepository;
import org.springframework.stereotype.Service;

/**
 * US-09, RN-21. An administrator creates a training group. The coach must be an active member holding COACH, the
 * accepted levels must be the association's own and the venue must exist in it; the rest is the aggregate's
 * (at least one level, a positive capacity). The venue's row lock is taken so that deleting that venue
 * (DeleteVenueService) cannot slip in between the check and the insert.
 */
@Service
public class CreateTrainingGroupService implements CreateTrainingGroupUseCase {

    private final MemberRepository members;
    private final AssociationRepository associations;
    private final VenueRepository venues;
    private final TrainingGroupRepository groups;
    private final UnitOfWork unitOfWork;

    public CreateTrainingGroupService(MemberRepository members, AssociationRepository associations,
                                      VenueRepository venues, TrainingGroupRepository groups,
                                      TransactionRunner transactions) {
        this.members = members;
        this.associations = associations;
        this.venues = venues;
        this.groups = groups;
        this.unitOfWork = new UnitOfWork(transactions);
    }

    @Override
    public TrainingGroup execute(CreateTrainingGroupCommand command) {
        return unitOfWork.retrying(() -> {
            var associationId = command.actor().associationId();
            Member admin = Lookups.member(members, associationId, command.actor().memberId());
            Permissions.requireAdmin(admin, "create training groups");
            Association association = Lookups.association(associations, associationId);
            venues.findByIdForUpdate(associationId, command.venueId())
                    .orElseThrow(() -> new VenueNotFoundException(command.venueId()));
            association.requireLevels(command.acceptedLevels());
            Lookups.coach(members, associationId, command.coachId());

            return groups.save(TrainingGroupFactory.create(associationId, command.name(), command.acceptedLevels(),
                    command.venueId(), command.schedule(), command.capacity(), command.coachId()));
        });
    }
}
