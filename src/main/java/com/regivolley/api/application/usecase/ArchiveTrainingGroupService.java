package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ArchiveTrainingGroupCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import org.springframework.stereotype.Service;

/** US-09. An administrator archives a group: it generates no more sessions; existing sessions are untouched. */
@Service
public class ArchiveTrainingGroupService implements ArchiveTrainingGroupUseCase {

    private final MemberRepository members;
    private final TrainingGroupRepository groups;
    private final UnitOfWork unitOfWork;

    public ArchiveTrainingGroupService(MemberRepository members, TrainingGroupRepository groups,
                                       TransactionRunner transactions) {
        this.members = members;
        this.groups = groups;
        this.unitOfWork = new UnitOfWork(transactions);
    }

    @Override
    public TrainingGroup execute(ArchiveTrainingGroupCommand command) {
        return unitOfWork.retrying(() -> {
            var associationId = command.actor().associationId();
            Member admin = Lookups.member(members, associationId, command.actor().memberId());
            Permissions.requireAdmin(admin, "archive training groups");
            TrainingGroup group = Lookups.group(groups, associationId, command.groupId());
            return groups.save(group.archive());
        });
    }
}
