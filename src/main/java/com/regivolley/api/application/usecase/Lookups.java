package com.regivolley.api.application.usecase;

import com.regivolley.api.domain.exception.AssociationNotFoundException;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.exception.SessionNotFoundException;
import com.regivolley.api.domain.exception.TrainingGroupNotFoundException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;

/**
 * "Load it or say it does not exist" for the aggregates every use case starts from. Every read goes
 * through a port that takes the tenant (architecture.md section 8), so an id from another
 * association is simply not found.
 */
final class Lookups {

    private Lookups() {
    }

    static Member member(MemberRepository members, AssociationId associationId, MemberId id) {
        return members.findById(associationId, id).orElseThrow(() -> new MemberNotFoundException(id));
    }

    static Session session(SessionRepository sessions, AssociationId associationId, SessionId id) {
        return sessions.findById(associationId, id).orElseThrow(() -> new SessionNotFoundException(id));
    }

    static Association association(AssociationRepository associations, AssociationId id) {
        return associations.findById(id).orElseThrow(() -> new AssociationNotFoundException(id));
    }

    static TrainingGroup group(TrainingGroupRepository groups, AssociationId associationId, TrainingGroupId id) {
        return groups.findById(associationId, id).orElseThrow(() -> new TrainingGroupNotFoundException(id));
    }
}
