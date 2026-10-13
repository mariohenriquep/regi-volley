package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.GetMyRolesQuery;
import com.regivolley.api.application.result.MyRoles;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The roles of the actor's own {@link Member}, read on every call (nothing is cached or put in the token, architecture.md section
 * 11), so a revoked role is gone on the next request. A deactivated member holds no powers whatever roles they were left with
 * ({@link Permissions}), so they are reported as a plain member. The tenant is the actor's. Read only, so no transaction of its own.
 */
@Service
public class GetMyRolesService implements GetMyRolesUseCase {

    private final MemberRepository members;

    public GetMyRolesService(MemberRepository members) {
        this.members = members;
    }

    @Override
    public MyRoles execute(GetMyRolesQuery query) {
        Member actor = Lookups.member(members, query.actor().associationId(), query.actor().memberId());
        return new MyRoles(actor.isActive() ? List.copyOf(actor.roles()) : List.of(MemberRole.MEMBER));
    }
}
