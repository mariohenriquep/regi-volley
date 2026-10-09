package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.RevokeRoleCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * An administrator takes a role away from a member. A member always keeps at least one role (the member
 * decides), and the association always keeps one active administrator: revoking ADMIN from an active
 * administrator goes through {@link AdminGuard}, which also covers an administrator revoking their own. Revoking ADMIN takes the association lock first, before any member is loaded
 * (see {@link AdminGuard}).
 */
@Service
public class RevokeRoleService implements RevokeRoleUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RevokeRoleService.class);

    private final MemberRepository members;
    private final AdminGuard adminGuard;
    private final UnitOfWork unitOfWork;

    public RevokeRoleService(MemberRepository members, AssociationRepository associations, TransactionRunner transactions) {
        this.members = members;
        this.adminGuard = new AdminGuard(associations, members);
        this.unitOfWork = new UnitOfWork(transactions);
    }

    @Override
    public Member execute(RevokeRoleCommand command) {
        Member revoked = unitOfWork.retrying(() -> attempt(command));
        // Audit line (threat model M5), by id only.
        LOG.info("Role revoked: associationId={} memberId={} role={} revokedBy={}", revoked.associationId(), revoked.id(), command.role(),
                command.actor().memberId());
        return revoked;
    }

    private Member attempt(RevokeRoleCommand command) {
        var associationId = command.actor().associationId();
        if (command.role() == MemberRole.ADMIN) {
            adminGuard.serialise(associationId);
        }
        Member actor = Lookups.member(members, associationId, command.actor().memberId());
        Permissions.requireAdmin(actor, "revoke roles");
        Member target = Lookups.member(members, associationId, command.memberId());
        Member revoked = target.revokeRole(command.role());
        if (command.role() == MemberRole.ADMIN && Permissions.isAdmin(target)) {
            adminGuard.requireAnotherActiveAdmin(associationId, target.id());
        }
        return members.save(revoked);
    }
}
