package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListPlansQuery;
import com.regivolley.api.application.result.PlanListEntry;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Level;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.PlanRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * US-19. An administrator lists the plans of their association, by name as the repository gives them, each with the levels it gives
 * access to (lowest first; none means no restriction, RN-14), named from the association's one load. Read only, so no transaction of
 * its own.
 */
@Service
public class ListPlansService implements ListPlansUseCase {

    private final MemberRepository members;
    private final AssociationRepository associations;
    private final PlanRepository plans;

    public ListPlansService(MemberRepository members, AssociationRepository associations, PlanRepository plans) {
        this.members = members;
        this.associations = associations;
        this.plans = plans;
    }

    @Override
    public List<PlanListEntry> execute(ListPlansQuery query) {
        var associationId = query.actor().associationId();
        Member admin = Lookups.member(members, associationId, query.actor().memberId());
        Permissions.requireAdmin(admin, "list plans");
        Association association = Lookups.association(associations, associationId);
        return plans.findAllByAssociation(associationId).stream()
                .map(plan -> new PlanListEntry(plan, association.levels().stream()
                        .filter(level -> plan.allowedLevels().contains(level.id()))
                        .sorted(Comparator.comparingInt(Level::rank)).toList()))
                .toList();
    }
}
