package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ApproveJoinRequestCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.result.JoinRequestApproval;
import com.regivolley.api.domain.port.Notifier;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.JoinRequestRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

/**
 * US-06, RN-20. An administrator approves a pending join request: the request becomes APPROVED and the member
 * is created at the entry level, both stored in the same transaction. The new member is told after the commit.
 * An approval racing a rejection (or another approval) loses on the request's version and is retried, then
 * refused by the request's state machine.
 */
@Service
public class ApproveJoinRequestService implements ApproveJoinRequestUseCase {

    private final AssociationRepository associations;
    private final MemberRepository members;
    private final JoinRequestRepository joinRequests;
    private final Notifier notifier;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ApproveJoinRequestService(AssociationRepository associations, MemberRepository members,
                                     JoinRequestRepository joinRequests, TransactionRunner transactions,
                                     Notifier notifier, Clock clock) {
        this.associations = associations;
        this.members = members;
        this.joinRequests = joinRequests;
        this.notifier = notifier;
        this.unitOfWork = new UnitOfWork(transactions);
        this.clock = clock;
    }

    @Override
    public JoinRequestApproval execute(ApproveJoinRequestCommand command) {
        return unitOfWork.retryingAndNotify(() -> attempt(command), notifier);
    }

    private Outcome<JoinRequestApproval> attempt(ApproveJoinRequestCommand command) {
        var associationId = command.actor().associationId();
        Member admin = Lookups.member(members, associationId, command.actor().memberId());
        Permissions.requireAdmin(admin, "approve join requests");
        JoinRequest request = Lookups.joinRequest(joinRequests, associationId, command.requestId());
        Association association = Lookups.association(associations, associationId);

        JoinRequestApproval approval = request.approve(association, admin.id(), clock);
        JoinRequest storedRequest = joinRequests.save(approval.request());
        Member storedMember = members.save(approval.member());
        return Outcome.of(new JoinRequestApproval(storedRequest, storedMember),
                List.of(n -> n.memberApproved(associationId, storedMember.id())));
    }
}
