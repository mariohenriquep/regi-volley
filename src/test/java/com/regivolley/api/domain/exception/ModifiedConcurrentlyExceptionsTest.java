package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.JoinRequestId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.PlanId;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ModifiedConcurrentlyExceptionsTest {

    @Test
    void everyVersionedAggregateHasATypedConflictCarryingOnlyItsKindAndId() {
        // Arrange
        UUID id = UUID.randomUUID();

        // Act
        AggregateModifiedConcurrentlyException[] all = {
                new SessionModifiedConcurrentlyException(new SessionId(id)),
                new SubscriptionModifiedConcurrentlyException(new SubscriptionId(id)),
                new TrainingGroupModifiedConcurrentlyException(new TrainingGroupId(id)),
                new AssociationModifiedConcurrentlyException(new AssociationId(id)),
                new MemberModifiedConcurrentlyException(new MemberId(id)),
                new JoinRequestModifiedConcurrentlyException(new JoinRequestId(id)),
                new PlanModifiedConcurrentlyException(new PlanId(id))};

        // Assert
        assertThat(all).extracting(AggregateModifiedConcurrentlyException::aggregate).containsExactly(
                "session", "subscription", "training group", "association", "member", "join request", "plan");
        assertThat(all).allSatisfy(ex -> {
            assertThat(ex.aggregateId()).isEqualTo(id);
            assertThat(ex.getMessage()).contains(id.toString()).contains(ex.aggregate());
            assertThat(ex).isNotInstanceOf(BusinessRuleException.class);
            assertThat(ex).isNotInstanceOf(java.util.ConcurrentModificationException.class);
        });
    }

    @Test
    void theSessionConflictExposesItsTypedId() {
        // Arrange
        SessionId id = SessionId.generate();

        // Act
        SessionModifiedConcurrentlyException ex = new SessionModifiedConcurrentlyException(id);

        // Assert
        assertThat(ex.sessionId()).isEqualTo(id);
    }
}
