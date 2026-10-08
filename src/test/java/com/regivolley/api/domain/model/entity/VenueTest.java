package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.InvalidCourtCountException;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.InvalidVenueException;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.VenueId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VenueTest {

    private static final AssociationId ASSOCIATION = AssociationId.generate();

    @Test
    void createsAVenueWithTrimmedTextAndVersionZero() {
        // Arrange
        // (the association from the constant)

        // Act
        Venue venue = Venue.create(ASSOCIATION, "  Pavilhao Central ", " Rua A 1, Lisboa ", 2);

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
        Executable act = () -> Venue.create(ASSOCIATION, "Pavilhao", "Rua A", courts);

        // Act
        InvalidCourtCountException ex = assertThrows(InvalidCourtCountException.class, act);

        // Assert
        assertThat(ex.requestedCourts()).isEqualTo(courts);
    }

    @Test
    void rejectsABlankNameOrAddressAndOverlongText() {
        // Arrange
        Executable blankName = () -> Venue.create(ASSOCIATION, " ", "Rua A", 1);
        Executable blankAddress = () -> Venue.create(ASSOCIATION, "Pavilhao", "", 1);
        Executable longName = () -> Venue.create(ASSOCIATION, "x".repeat(Venue.MAX_NAME_LENGTH + 1), "Rua A", 1);
        Executable longAddress = () -> Venue.create(ASSOCIATION, "Pavilhao", "x".repeat(Venue.MAX_ADDRESS_LENGTH + 1), 1);

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
    void editKeepsIdentityTenantAndVersion() {
        // Arrange
        Venue venue = Venue.reconstruct(VenueId.generate(), ASSOCIATION, "Old", "Old street", 1, 6L);

        // Act
        Venue edited = venue.edit("New", "New street", 3);

        // Assert
        assertThat(edited.id()).isEqualTo(venue.id());
        assertThat(edited.associationId()).isEqualTo(ASSOCIATION);
        assertThat(edited.version()).isEqualTo(6L);
        assertThat(edited.name()).isEqualTo("New");
        assertThat(edited.address()).isEqualTo("New street");
        assertThat(edited.courts()).isEqualTo(3);
        assertThat(venue.name()).isEqualTo("Old");
    }

    @Test
    void editRevalidates() {
        // Arrange
        Venue venue = Venue.create(ASSOCIATION, "Pavilhao", "Rua A", 1);
        Executable act = () -> venue.edit("Pavilhao", "Rua A", 0);

        // Act
        InvalidCourtCountException ex = assertThrows(InvalidCourtCountException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("court");
    }

    @Test
    void rejectsANegativeVersionAndMissingParts() {
        // Arrange
        Executable negative = () -> Venue.reconstruct(VenueId.generate(), ASSOCIATION, "P", "A", 1, -1L);
        Executable noId = () -> Venue.reconstruct(null, ASSOCIATION, "P", "A", 1, 0L);
        Executable noAssociation = () -> Venue.reconstruct(VenueId.generate(), null, "P", "A", 1, 0L);

        // Act
        InvalidVenueException ex = assertThrows(InvalidVenueException.class, negative);
        NullPointerException id = assertThrows(NullPointerException.class, noId);
        NullPointerException association = assertThrows(NullPointerException.class, noAssociation);

        // Assert
        assertThat(ex.getMessage()).contains("version");
        assertThat(id.getMessage()).contains("id");
        assertThat(association.getMessage()).contains("associationId");
    }

    @Test
    void equalityIsByIdAndToStringPrintsIdsOnly() {
        // Arrange
        Venue venue = Venue.create(ASSOCIATION, "Pavilhao", "Rua A", 1);
        Venue renamed = venue.edit("Other", "Rua B", 2);
        Venue another = Venue.create(ASSOCIATION, "Pavilhao", "Rua A", 1);

        // Act
        String text = venue.toString();

        // Assert
        assertThat(venue).isEqualTo(renamed).hasSameHashCodeAs(renamed).isNotEqualTo(another).isNotEqualTo("x");
        assertThat(text).contains(venue.id().toString()).doesNotContain("Rua A");
    }
}
