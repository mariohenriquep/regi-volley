package com.regivolley.api.domain.factory;

import com.regivolley.api.domain.exception.InvalidCourtCountException;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.InvalidVenueException;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.VenueId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VenueFactoryTest {

    private static final AssociationId ASSOCIATION = AssociationId.generate();

    @Test
    void reconstituteKeepsTheStoredIdentityAndVersion() {
        // Arrange
        VenueId id = VenueId.generate();

        // Act
        Venue venue = VenueFactory.reconstitute(id, ASSOCIATION, "Pavilhao", "Rua A", 3, 7L);

        // Assert
        assertThat(venue.id()).isEqualTo(id);
        assertThat(venue.version()).isEqualTo(7L);
    }

    @Test
    void createsAVenueWithTrimmedTextAndVersionZero() {
        // Arrange
        // (the association from the constant)

        // Act
        Venue venue = VenueFactory.create(ASSOCIATION, "  Pavilhao Central ", " Rua A 1, Lisboa ", 2);

        // Assert
        assertThat(venue.id()).isNotNull();
        assertThat(venue.associationId()).isEqualTo(ASSOCIATION);
        assertThat(venue.name()).isEqualTo("Pavilhao Central");
        assertThat(venue.address()).isEqualTo("Rua A 1, Lisboa");
        assertThat(venue.courts()).isEqualTo(2);
        assertThat(venue.version()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsLessThanOneCourt(int courts) {
        // Arrange
        Executable act = () -> VenueFactory.create(ASSOCIATION, "Pavilhao", "Rua A", courts);

        // Act
        InvalidCourtCountException ex = assertThrows(InvalidCourtCountException.class, act);

        // Assert
        assertThat(ex.requestedCourts()).isEqualTo(courts);
    }

    @Test
    void rejectsABlankNameOrAddressAndOverlongText() {
        // Arrange
        Executable blankName = () -> VenueFactory.create(ASSOCIATION, " ", "Rua A", 1);
        Executable blankAddress = () -> VenueFactory.create(ASSOCIATION, "Pavilhao", "", 1);
        Executable longName = () -> VenueFactory.create(ASSOCIATION, "x".repeat(Venue.MAX_NAME_LENGTH + 1), "Rua A", 1);
        Executable longAddress = () -> VenueFactory.create(ASSOCIATION, "Pavilhao", "x".repeat(Venue.MAX_ADDRESS_LENGTH + 1), 1);

        // Act
        InvalidFieldException name = assertThrows(InvalidFieldException.class, blankName);
        InvalidFieldException address = assertThrows(InvalidFieldException.class, blankAddress);
        InvalidFieldException tooLongName = assertThrows(InvalidFieldException.class, longName);
        InvalidFieldException tooLongAddress = assertThrows(InvalidFieldException.class, longAddress);

        // Assert
        assertThat(name.field()).isEqualTo("venue name");
        assertThat(address.field()).isEqualTo("venue address");
        assertThat(tooLongName.field()).isEqualTo("venue name");
        assertThat(tooLongAddress.field()).isEqualTo("venue address");
    }

    @Test
    void rejectsANegativeVersionAndMissingParts() {
        // Arrange
        Executable negative = () -> VenueFactory.reconstitute(VenueId.generate(), ASSOCIATION, "P", "A", 1, -1L);
        Executable noId = () -> VenueFactory.reconstitute(null, ASSOCIATION, "P", "A", 1, 0L);
        Executable noAssociation = () -> VenueFactory.reconstitute(VenueId.generate(), null, "P", "A", 1, 0L);

        // Act
        InvalidVenueException ex = assertThrows(InvalidVenueException.class, negative);
        NullPointerException id = assertThrows(NullPointerException.class, noId);
        NullPointerException association = assertThrows(NullPointerException.class, noAssociation);

        // Assert
        assertThat(ex.getMessage()).contains("version");
        assertThat(id.getMessage()).contains("id");
        assertThat(association.getMessage()).contains("associationId");
    }
}
