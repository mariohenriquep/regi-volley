package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.GrantRoleCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;

/** An administrator gives a member a role. Granting a role the member already holds changes nothing. */
@Service
public class GrantRoleService implements GrantRoleUseCase {

    private final MemberRepository members;
    private final UnitOfWork unitOfWork;

    public GrantRoleService(MemberRepository members, TransactionRunner transactions) {
        this.members = members;
        this.unitOfWork = new UnitOfWork(transactions);
    }

    @Override
    public Member execute(GrantRoleCommand command) {
        return unitOfWork.retrying(() -> attempt(command));
    }

    private Member attempt(GrantRoleCommand command) {
        var associationId = command.actor().associationId();
        Member actor = Lookups.member(members, associationId, command.actor().memberId());
        Permissions.requireAdmin(actor, "grant roles");
        Member target = Lookups.member(members, associationId, command.memberId());
        return members.save(target.grantRole(command.role()));
    }
}
