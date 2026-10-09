package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.usecase.MemberHistoryUseCase;
import com.regivolley.api.application.usecase.MyPlanUseCase;
import com.regivolley.api.infrastructure.security.AuthenticatedActor;
import com.regivolley.api.infrastructure.security.CurrentActor;
import com.regivolley.api.infrastructure.web.dto.MemberHistoryResponse;
import com.regivolley.api.infrastructure.web.dto.MyPlanResponse;
import com.regivolley.api.infrastructure.web.mapper.BookingWebMapper;
import com.regivolley.api.infrastructure.web.mapper.SubscriptionWebMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What members read about themselves: their plan and balance (US-23) and their history (US-18). Always the caller's own data. */
@RestController
@RequestMapping("/api/v1/me")
public class MemberSelfController {

    private final MyPlanUseCase myPlan;
    private final MemberHistoryUseCase memberHistory;

    public MemberSelfController(MyPlanUseCase myPlan, MemberHistoryUseCase memberHistory) {
        this.myPlan = myPlan;
        this.memberHistory = memberHistory;
    }

    @GetMapping("/plan")
    public MyPlanResponse plan(@CurrentActor AuthenticatedActor caller) {
        return SubscriptionWebMapper.toResponse(myPlan.execute(SubscriptionWebMapper.myPlanQuery(caller.actor())));
    }

    @GetMapping("/history")
    public MemberHistoryResponse history(@CurrentActor AuthenticatedActor caller) {
        return BookingWebMapper.toResponse(memberHistory.execute(BookingWebMapper.historyQuery(caller.actor())));
    }
}
