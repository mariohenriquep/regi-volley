package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ChangeMemberLevelCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * US-07, RN-20. A coach or an administrator moves a member to another level of the association; the member
 * records who did it and when, and the new level applies to the bookings made from then on. Moving a member to the
 * level they already have stores nothing.
 */
@Service
public class ChangeMemberLevelService implements ChangeMemberLevelUseCase {

    private final MemberRepository members;
    private final AssociationRepository associations;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ChangeMemberLevelService(MemberRepository members, AssociationRepository associations,
                                    TransactionRunner transactions, Clock clock) {
        this.members = members;
        this.associations = associations;
        this.unitOfWork = new UnitOfWork(transactions);
        this.clock = clock;
    }

    @Override
    public Member execute(ChangeMemberLevelCommand command) {
        return unitOfWork.retrying(() -> attempt(command));
    }

    private Member attempt(ChangeMemberLevelCommand command) {
        var associationId = command.actor().associationId();
        Member actor = Lookups.member(members, associationId, command.actor().memberId());
        Permissions.requireAdminOrCoach(actor, "change a member's level");
        Member target = Lookups.member(members, associationId, command.memberId());
        Association association = Lookups.association(associations, associationId);

        Member changed = target.changeLevel(association, command.levelId(), actor.id(), clock);
        return changed == target ? target : members.save(changed);
    }
}
