package com.regivolley.api.domain.model.valueobject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NoShowPolicyTest {

    @Test
    void defaultsToThreeNoShowsAMonth() {
        // Arrange
        // (no input)

        // Act
        NoShowPolicy policy = NoShowPolicy.defaults();

        // Assert
        assertThat(policy.monthlyLimit()).isEqualTo(3);
    }

    @Test
    void reachesTheLimitOnlyAtTheLimit() {
        // Arrange
        NoShowPolicy policy = new NoShowPolicy(3);

        // Act
        boolean below = policy.isReachedBy(2);
        boolean at = policy.isReachedBy(3);
        boolean above = policy.isReachedBy(4);

        // Assert
        assertThat(below).isFalse();
        assertThat(at).isTrue();
        assertThat(above).isFalse();
    }

    @Test
    void isNearTheLimitOneNoShowBeforeItAndAtIt() {
        // Arrange
        NoShowPolicy policy = new NoShowPolicy(3);

        // Act
        boolean far = policy.isNear(1);
        boolean oneBefore = policy.isNear(2);
        boolean at = policy.isNear(3);

        // Assert
        assertThat(far).isFalse();
        assertThat(oneBefore).isTrue();
        assertThat(at).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    void rejectsALimitBelowOne(int limit) {
        // Arrange
        Executable act = () -> new NoShowPolicy(limit);

        // Act
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("monthlyLimit");
    }
}
