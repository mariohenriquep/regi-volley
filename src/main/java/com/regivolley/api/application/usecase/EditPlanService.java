package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.EditPlanCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.PlanRepository;
import org.springframework.stereotype.Service;

/**
 * US-19. An administrator edits a plan. Subscriptions already sold keep the terms and the price they snapshotted, so a new
 * price applies only to subscriptions assigned from now on. There is no
 * delete: subscriptions and payments keep pointing at their plan.
 */
@Service
public class EditPlanService implements EditPlanUseCase {

    private final MemberRepository members;
    private final AssociationRepository associations;
    private final PlanRepository plans;
    private final UnitOfWork unitOfWork;

    public EditPlanService(MemberRepository members, AssociationRepository associations, PlanRepository plans,
                           TransactionRunner transactions) {
        this.members = members;
        this.associations = associations;
        this.plans = plans;
        this.unitOfWork = new UnitOfWork(transactions);
    }

    @Override
    public Plan execute(EditPlanCommand command) {
        return unitOfWork.retrying(() -> {
            var associationId = command.actor().associationId();
            Member admin = Lookups.member(members, associationId, command.actor().memberId());
            Permissions.requireAdmin(admin, "edit plans");
            Plan plan = Lookups.plan(plans, associationId, command.planId());
            Association association = Lookups.association(associations, associationId);
            association.requireLevels(command.terms().allowedLevels());
            return plans.save(plan.edit(command.name(), command.terms(), command.price(), command.validityDays()));
        });
    }
}
