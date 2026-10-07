package com.regivolley.api.domain.model.valueobject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TypedIdsTest {

    @Test
    void wrapsAUuidAndComparesByValue() {
        // Arrange
        UUID uuid = UUID.randomUUID();

        // Act
        SessionId first = SessionId.of(uuid);
        SessionId second = new SessionId(uuid);

        // Assert
        assertThat(first).isEqualTo(second);
        assertThat(first.value()).isEqualTo(uuid);
        assertThat(first.toString()).isEqualTo(uuid.toString());
    }

    @Test
    void generatesDistinctIds() {
        // Arrange
        // (no input)

        // Act
        MemberId a = MemberId.generate();
        MemberId b = MemberId.generate();

        // Assert
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void rejectsNull() {
        // Arrange
        Executable act = () -> new AssociationId(null);

        // Act
        NullPointerException ex = assertThrows(NullPointerException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("AssociationId");
    }

    @Test
    void everyIdTypeGeneratesAndWraps() {
        // Arrange
        UUID uuid = UUID.randomUUID();

        // Act
        BookingId booking = BookingId.of(uuid);
        TrainingGroupId group = TrainingGroupId.of(uuid);
        PlanId plan = PlanId.of(uuid);
        SubscriptionId subscription = SubscriptionId.of(uuid);
        LevelId level = LevelId.of(uuid);

        // Assert
        assertThat(booking.value()).isEqualTo(uuid);
        assertThat(group.value()).isEqualTo(uuid);
        assertThat(plan.value()).isEqualTo(uuid);
        assertThat(subscription.value()).isEqualTo(uuid);
        assertThat(level.value()).isEqualTo(uuid);
        assertThat(PlanId.generate()).isNotNull();
        assertThat(SubscriptionId.generate()).isNotNull();
        assertThat(LevelId.generate()).isNotNull();
        assertThat(BookingId.generate()).isNotNull();
        assertThat(TrainingGroupId.generate()).isNotNull();
        assertThat(AssociationId.generate()).isNotNull();
        assertThat(SessionId.generate()).isNotNull();
    }
}
