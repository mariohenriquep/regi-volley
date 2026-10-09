package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.usecase.ApproveJoinRequestUseCase;
import com.regivolley.api.application.usecase.ListPendingJoinRequestsUseCase;
import com.regivolley.api.application.usecase.RejectJoinRequestUseCase;
import com.regivolley.api.infrastructure.security.AuthenticatedActor;
import com.regivolley.api.infrastructure.security.CurrentActor;
import com.regivolley.api.infrastructure.web.dto.JoinRequestApprovalResponse;
import com.regivolley.api.infrastructure.web.dto.JoinRequestResponse;
import com.regivolley.api.infrastructure.web.dto.RejectionRequest;
import com.regivolley.api.infrastructure.web.mapper.JoinRequestWebMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The join requests of the caller's association (US-06), for administrators (the use cases check). */
@RestController
@RequestMapping("/api/v1/join-requests")
public class JoinRequestController {

    private final ListPendingJoinRequestsUseCase listPending;
    private final ApproveJoinRequestUseCase approve;
    private final RejectJoinRequestUseCase reject;

    public JoinRequestController(ListPendingJoinRequestsUseCase listPending, ApproveJoinRequestUseCase approve,
                                 RejectJoinRequestUseCase reject) {
        this.listPending = listPending;
        this.approve = approve;
        this.reject = reject;
    }

    /** The requests waiting for a decision. */
    @GetMapping
    public List<JoinRequestResponse> pending(@CurrentActor AuthenticatedActor caller) {
        return JoinRequestWebMapper.toResponses(listPending.execute(JoinRequestWebMapper.listQuery(caller.actor())));
    }

    @PostMapping("/{requestId}/approval")
    public JoinRequestApprovalResponse approve(@CurrentActor AuthenticatedActor caller, @PathVariable UUID requestId) {
        return JoinRequestWebMapper.toResponse(approve.execute(JoinRequestWebMapper.approveCommand(caller.actor(), requestId)));
    }

    @PostMapping("/{requestId}/rejection")
    public JoinRequestResponse reject(@CurrentActor AuthenticatedActor caller, @PathVariable UUID requestId,
                                      @Valid @RequestBody RejectionRequest body) {
        return JoinRequestWebMapper.toResponse(reject.execute(JoinRequestWebMapper.rejectCommand(caller.actor(), requestId, body)));
    }
}
