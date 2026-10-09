package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListPendingJoinRequestsQuery;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.repository.JoinRequestRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * US-06. An administrator sees the join requests of their association that have not been decided, oldest first as the repository
 * gives them. They carry the applicant's contact data, so only an active administrator may read them. Read only, so no
 * transaction of its own.
 */
@Service
public class ListPendingJoinRequestsService implements ListPendingJoinRequestsUseCase {

    private final MemberRepository members;
    private final JoinRequestRepository joinRequests;

    public ListPendingJoinRequestsService(MemberRepository members, JoinRequestRepository joinRequests) {
        this.members = members;
        this.joinRequests = joinRequests;
    }

    @Override
    public List<JoinRequest> execute(ListPendingJoinRequestsQuery query) {
        var associationId = query.actor().associationId();
        Member admin = Lookups.member(members, associationId, query.actor().memberId());
        Permissions.requireAdmin(admin, "list join requests");
        return joinRequests.findPending(associationId);
    }
}
