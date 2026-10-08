package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.EditTrainingGroupCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.domain.exception.TrainingGroupArchivedException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import org.springframework.stereotype.Service;

/**
 * US-09. An administrator edits a group's name, accepted levels, schedule, capacity and coach, with the same checks
 * as when creating it. An archived group is refused first, before any other check. Sessions already generated are not touched.
 */
@Service
public class EditTrainingGroupService implements EditTrainingGroupUseCase {

    private final MemberRepository members;
    private final AssociationRepository associations;
    private final TrainingGroupRepository groups;
    private final UnitOfWork unitOfWork;

    public EditTrainingGroupService(MemberRepository members, AssociationRepository associations,
                                    TrainingGroupRepository groups, TransactionRunner transactions) {
        this.members = members;
        this.associations = associations;
        this.groups = groups;
        this.unitOfWork = new UnitOfWork(transactions);
    }

    @Override
    public TrainingGroup execute(EditTrainingGroupCommand command) {
        return unitOfWork.retrying(() -> {
            var associationId = command.actor().associationId();
            Member admin = Lookups.member(members, associationId, command.actor().memberId());
            Permissions.requireAdmin(admin, "edit training groups");
            TrainingGroup group = Lookups.group(groups, associationId, command.groupId());
            if (!group.isActive()) {
                throw new TrainingGroupArchivedException(group.id());
            }
            Association association = Lookups.association(associations, associationId);
            association.requireLevels(command.acceptedLevels());
            Lookups.coach(members, associationId, command.coachId());

            TrainingGroup edited = group.rename(command.name())
                    .changeAcceptedLevels(command.acceptedLevels())
                    .changeSchedule(command.schedule())
                    .changeCapacity(command.capacity())
                    .changeCoach(command.coachId());
            return groups.save(edited);
        });
    }
}
