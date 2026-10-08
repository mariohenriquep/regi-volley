package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.RejectJoinRequestCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.port.Notifier;
import com.regivolley.api.domain.repository.JoinRequestRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

/** US-06. An administrator rejects a pending join request, with an optional reason; the visitor is told after the commit. */
@Service
public class RejectJoinRequestService implements RejectJoinRequestUseCase {

    private final MemberRepository members;
    private final JoinRequestRepository joinRequests;
    private final Notifier notifier;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public RejectJoinRequestService(MemberRepository members, JoinRequestRepository joinRequests,
                                    TransactionRunner transactions, Notifier notifier, Clock clock) {
        this.members = members;
        this.joinRequests = joinRequests;
        this.notifier = notifier;
        this.unitOfWork = new UnitOfWork(transactions);
        this.clock = clock;
    }

    @Override
    public JoinRequest execute(RejectJoinRequestCommand command) {
        return unitOfWork.retryingAndNotify(() -> attempt(command), notifier);
    }

    private Outcome<JoinRequest> attempt(RejectJoinRequestCommand command) {
        var associationId = command.actor().associationId();
        Member admin = Lookups.member(members, associationId, command.actor().memberId());
        Permissions.requireAdmin(admin, "reject join requests");
        JoinRequest request = Lookups.joinRequest(joinRequests, associationId, command.requestId());

        JoinRequest stored = joinRequests.save(request.reject(admin.id(), command.reason(), clock));
        return Outcome.of(stored, List.of(n -> n.joinRequestRejected(associationId, stored.id())));
    }
}
