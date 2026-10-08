package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.CreatePlanCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.domain.factory.PlanFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.PlanRepository;
import org.springframework.stereotype.Service;

/**
 * US-19, RN-13, RN-14. An administrator creates a plan; the levels it allows must be the association's own. The
 * type, credits and validity rules are the aggregate's.
 */
@Service
public class CreatePlanService implements CreatePlanUseCase {

    private final MemberRepository members;
    private final AssociationRepository associations;
    private final PlanRepository plans;
    private final UnitOfWork unitOfWork;

    public CreatePlanService(MemberRepository members, AssociationRepository associations, PlanRepository plans,
                             TransactionRunner transactions) {
        this.members = members;
        this.associations = associations;
        this.plans = plans;
        this.unitOfWork = new UnitOfWork(transactions);
    }

    @Override
    public Plan execute(CreatePlanCommand command) {
        return unitOfWork.retrying(() -> {
            var associationId = command.actor().associationId();
            Member admin = Lookups.member(members, associationId, command.actor().memberId());
            Permissions.requireAdmin(admin, "create plans");
            Association association = Lookups.association(associations, associationId);
            association.requireLevels(command.terms().allowedLevels());
            return plans.save(PlanFactory.create(associationId, command.name(), command.terms(), command.price(),
                    command.validityDays()));
        });
    }
}
