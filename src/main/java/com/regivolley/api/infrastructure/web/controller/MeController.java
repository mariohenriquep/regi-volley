package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.infrastructure.security.AuthenticatedActor;
import com.regivolley.api.infrastructure.security.CurrentActor;
import com.regivolley.api.infrastructure.web.dto.MeResponse;
import com.regivolley.api.infrastructure.web.mapper.MeWebMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authenticated probe: answers with the ids the security layer resolved for the caller. It proves a token, the
 * principal resolution and the tenant binding end to end, and tells the PWA who it is acting as. It reads nothing else.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    @GetMapping
    public MeResponse me(@CurrentActor AuthenticatedActor caller) {
        return MeWebMapper.toResponse(caller.actor());
    }
}
