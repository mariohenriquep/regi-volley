package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.Actor;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;

import java.util.function.UnaryOperator;

/**
 * The shape shared by the four level operations (US-03): an administrator's change of the association's levels,
 * decided by the {@code Association} aggregate, stored with its optimistic lock and retried in a new transaction
 * when another edit won the race.
 */
final class LevelChanger {

    private final AssociationRepository associations;
    private final MemberRepository members;
    private final UnitOfWork unitOfWork;

    LevelChanger(AssociationRepository associations, MemberRepository members, TransactionRunner transactions) {
        this.associations = associations;
        this.members = members;
        this.unitOfWork = new UnitOfWork(transactions);
    }

    Association change(Actor actor, UnaryOperator<Association> change) {
        return unitOfWork.retrying(() -> {
            Member admin = Lookups.member(members, actor.associationId(), actor.memberId());
            Permissions.requireAdmin(admin, "manage the levels");
            Association association = Lookups.association(associations, actor.associationId());
            return associations.save(change.apply(association));
        });
    }
}
