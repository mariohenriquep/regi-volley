package com.regivolley.api.application.usecase;

import com.regivolley.api.domain.exception.AssociationNotFoundException;
import com.regivolley.api.domain.exception.InvalidCoachException;
import com.regivolley.api.domain.exception.JoinRequestNotFoundException;
import com.regivolley.api.domain.exception.PaymentNotFoundException;
import com.regivolley.api.domain.exception.PlanNotFoundException;
import com.regivolley.api.domain.exception.SubscriptionNotFoundException;
import com.regivolley.api.domain.exception.VenueNotFoundException;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.JoinRequestId;
import com.regivolley.api.domain.model.valueobject.PaymentId;
import com.regivolley.api.domain.model.valueobject.PlanId;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.repository.JoinRequestRepository;
import com.regivolley.api.domain.repository.PaymentRepository;
import com.regivolley.api.domain.repository.PlanRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import com.regivolley.api.domain.repository.VenueRepository;
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

    /** The member as a coach: unknown to the association, inactive or without the COACH role is an {@link InvalidCoachException}. */
    static Member coach(MemberRepository members, AssociationId associationId, MemberId id) {
        return members.findById(associationId, id).orElseThrow(() -> new InvalidCoachException(id)).requireCanCoach();
    }

    static JoinRequest joinRequest(JoinRequestRepository joinRequests, AssociationId associationId, JoinRequestId id) {
        return joinRequests.findById(associationId, id).orElseThrow(() -> new JoinRequestNotFoundException(id));
    }

    static Plan plan(PlanRepository plans, AssociationId associationId, PlanId id) {
        return plans.findById(associationId, id).orElseThrow(() -> new PlanNotFoundException(id));
    }

    static Subscription subscription(SubscriptionRepository subscriptions, AssociationId associationId, SubscriptionId id) {
        return subscriptions.findById(associationId, id).orElseThrow(() -> new SubscriptionNotFoundException(id));
    }

    static Venue venue(VenueRepository venues, AssociationId associationId, VenueId id) {
        return venues.findById(associationId, id).orElseThrow(() -> new VenueNotFoundException(id));
    }

    static Payment payment(PaymentRepository payments, AssociationId associationId, PaymentId id) {
        return payments.findById(associationId, id).orElseThrow(() -> new PaymentNotFoundException(id));
    }
}
