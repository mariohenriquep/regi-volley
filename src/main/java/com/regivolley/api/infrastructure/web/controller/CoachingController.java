package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.usecase.ListCoachingSessionsUseCase;
import com.regivolley.api.infrastructure.security.AuthenticatedActor;
import com.regivolley.api.infrastructure.security.CurrentActor;
import com.regivolley.api.infrastructure.web.dto.CoachingSessionResponse;
import com.regivolley.api.infrastructure.web.dto.PlausibleDate;
import com.regivolley.api.infrastructure.web.mapper.CoachingWebMapper;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * What the staff see of their own sessions: the coach the sessions they coach, an administrator all of them (decided by the use case
 * from the caller's roles, never by the web layer). The tenant is the token's.
 */
@RestController
@RequestMapping("/api/v1/coaching")
public class CoachingController {

    private final ListCoachingSessionsUseCase listCoachingSessions;

    public CoachingController(ListCoachingSessionsUseCase listCoachingSessions) {
        this.listCoachingSessions = listCoachingSessions;
    }

    /** The staff's sessions in the Lisbon week (Monday to Sunday) that contains {@code weekOf}; this week when it is absent. */
    @GetMapping("/sessions")
    public List<CoachingSessionResponse> week(@CurrentActor AuthenticatedActor caller,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @PlausibleDate LocalDate weekOf) {
        return CoachingWebMapper.toResponses(listCoachingSessions.execute(CoachingWebMapper.weekQuery(caller.actor(), weekOf)));
    }
}
