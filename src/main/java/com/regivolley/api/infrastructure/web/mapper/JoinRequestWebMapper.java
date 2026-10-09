package com.regivolley.api.infrastructure.web.mapper;

import com.regivolley.api.application.command.Actor;
import com.regivolley.api.application.command.ApproveJoinRequestCommand;
import com.regivolley.api.application.command.ListPendingJoinRequestsQuery;
import com.regivolley.api.application.command.RejectJoinRequestCommand;
import com.regivolley.api.application.result.JoinRequestApproval;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.valueobject.JoinRequestId;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import com.regivolley.api.infrastructure.web.dto.JoinRequestApprovalResponse;
import com.regivolley.api.infrastructure.web.dto.JoinRequestResponse;
import com.regivolley.api.infrastructure.web.dto.RejectionRequest;

import java.util.List;
import java.util.UUID;

/** Join requests as an administrator handles them (US-06). */
public final class JoinRequestWebMapper {

    private JoinRequestWebMapper() {
    }

    public static ListPendingJoinRequestsQuery listQuery(Actor actor) {
        return new ListPendingJoinRequestsQuery(actor);
    }

    public static ApproveJoinRequestCommand approveCommand(Actor actor, UUID requestId) {
        return new ApproveJoinRequestCommand(actor, JoinRequestId.of(requestId));
    }

    public static RejectJoinRequestCommand rejectCommand(Actor actor, UUID requestId, RejectionRequest body) {
        return new RejectJoinRequestCommand(actor, JoinRequestId.of(requestId), body.reason());
    }

    public static JoinRequestResponse toResponse(JoinRequest request) {
        return new JoinRequestResponse(request.id().value(), request.name(), request.email().value(),
                request.phone().map(PhoneNumber::value).orElse(null), request.status().name(), request.requestedAt(),
                request.decidedAt().orElse(null), request.rejectionReason().orElse(null), request.consent().policyVersion());
    }

    public static List<JoinRequestResponse> toResponses(List<JoinRequest> requests) {
        return requests.stream().map(JoinRequestWebMapper::toResponse).toList();
    }

    public static JoinRequestApprovalResponse toResponse(JoinRequestApproval approval) {
        return new JoinRequestApprovalResponse(toResponse(approval.request()), approval.member().id().value());
    }
}
