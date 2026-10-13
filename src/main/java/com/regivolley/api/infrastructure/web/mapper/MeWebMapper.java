package com.regivolley.api.infrastructure.web.mapper;

import com.regivolley.api.application.command.Actor;
import com.regivolley.api.application.command.GetMyRolesQuery;
import com.regivolley.api.application.result.MyRoles;
import com.regivolley.api.infrastructure.web.dto.MeResponse;

/** The caller's {@link Actor} and roles to their wire shape. */
public final class MeWebMapper {

    private MeWebMapper() {
    }

    public static GetMyRolesQuery rolesQuery(Actor caller) {
        return new GetMyRolesQuery(caller);
    }

    public static MeResponse toResponse(Actor caller, MyRoles roles) {
        return new MeResponse(caller.memberId().value(), caller.associationId().value(),
                roles.roles().stream().map(MemberWebMapper::toWire).toList());
    }
}
