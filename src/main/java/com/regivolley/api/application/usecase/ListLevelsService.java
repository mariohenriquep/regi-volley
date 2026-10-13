package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListLevelsQuery;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;

/**
 * US-03. An administrator reads the levels of their association: the association itself, which knows its levels in order and the
 * entry level new members start at (RN-20). The tenant is the actor's. Read only, so no transaction of its own.
 */
@Service
public class ListLevelsService implements ListLevelsUseCase {

    private final MemberRepository members;
    private final AssociationRepository associations;

    public ListLevelsService(MemberRepository members, AssociationRepository associations) {
        this.members = members;
        this.associations = associations;
    }

    @Override
    public Association execute(ListLevelsQuery query) {
        var associationId = query.actor().associationId();
        Member admin = Lookups.member(members, associationId, query.actor().memberId());
        Permissions.requireAdmin(admin, "see the levels");
        return Lookups.association(associations, associationId);
    }
}
