package com.regivolley.api.domain.model.valueobject;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class BookingStatusTest {

    @ParameterizedTest
    @CsvSource({"WAITLISTED,false", "CONFIRMED,true", "ATTENDED,true", "NO_SHOW,true", "CANCELLED,false"})
    void holdsSeatIsTrueExactlyForTheBookingsThatTookASeat(BookingStatus status, boolean expected) {
        // Arrange
        // (the status)

        // Act
        boolean holdsSeat = status.holdsSeat();

        // Assert
        assertThat(holdsSeat).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"WAITLISTED,true", "CONFIRMED,true", "ATTENDED,false", "NO_SHOW,false", "CANCELLED,false"})
    void isActiveIsUnchangedByHoldsSeat(BookingStatus status, boolean expected) {
        // Arrange
        // (the status)

        // Act
        boolean active = status.isActive();

        // Assert
        assertThat(active).isEqualTo(expected);
    }
}
