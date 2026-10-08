package com.regivolley.api.infrastructure.web.mapper;

import com.regivolley.api.application.command.Actor;
import com.regivolley.api.infrastructure.web.dto.MeResponse;

/** The caller's {@link Actor} to its wire shape. */
public final class MeWebMapper {

    private MeWebMapper() {
    }

    public static MeResponse toResponse(Actor caller) {
        return new MeResponse(caller.memberId().value(), caller.associationId().value());
    }
}
