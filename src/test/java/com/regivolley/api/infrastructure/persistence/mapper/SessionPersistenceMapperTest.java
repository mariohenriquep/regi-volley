package com.regivolley.api.infrastructure.persistence.mapper;

import com.regivolley.api.domain.factory.SessionFactory;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.infrastructure.persistence.adapter.Fixtures;
import com.regivolley.api.infrastructure.persistence.entity.BookingJpaEntity;
import com.regivolley.api.infrastructure.persistence.entity.SessionJpaEntity;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static com.regivolley.api.infrastructure.persistence.adapter.Fixtures.SESSION_START;
import static org.assertj.core.api.Assertions.assertThat;

class SessionPersistenceMapperTest {

    private static Session bookedBy(MemberId... members) {
        Session session = Fixtures.session(AssociationId.generate(), SESSION_START, 1);
        long offset = 0;
        for (MemberId member : members) {
            session = Fixtures.book(session, member, offset++);
        }
        return session;
    }

    @Test
    void aNewEntityGetsAllTheScalarsAndOneRowPerBookingAndMapsBackToTheSameSession() {
        // Arrange
        Session session = bookedBy(MemberId.generate(), MemberId.generate());
        SessionJpaEntity entity = new SessionJpaEntity();

        // Act
        SessionPersistenceMapper.apply(session, entity);
        Session mapped = SessionPersistenceMapper.toDomain(entity);

        // Assert
        assertThat(entity.getBookings()).hasSize(2).allMatch(row -> row.getSession() == entity);
        assertThat(entity.getVersion()).as("the version belongs to the persistence layer").isNull();
        assertThat(mapped).usingRecursiveComparison().isEqualTo(session);
        assertThat(mapped.version()).isZero();
    }

    @Test
    void anExistingEntityIsSyncedInPlaceRowsUpdatedAddedAndRemoved() {
        // Arrange
        MemberId staying = MemberId.generate();
        MemberId leaving = MemberId.generate();
        Session before = bookedBy(staying, leaving);
        SessionJpaEntity entity = new SessionJpaEntity();
        SessionPersistenceMapper.apply(before, entity);
        BookingJpaEntity stayingRow = entity.getBookings().stream()
                .filter(row -> row.getMemberId().equals(staying.value())).findFirst().orElseThrow();
        Booking stayingBooking = before.bookings().stream()
                .filter(b -> b.memberId().equals(staying)).findFirst().orElseThrow();
        Booking leavingBooking = before.bookings().stream()
                .filter(b -> b.memberId().equals(leaving)).findFirst().orElseThrow();
        Session cancelledLeaving = before.cancelBooking(leavingBooking.id(), Fixtures.POLICY,
                Fixtures.at(Fixtures.NOW.plusSeconds(100)), member -> true).session();
        MemberId newcomer = MemberId.generate();
        Session after = Fixtures.book(cancelledLeaving, newcomer, 50);

        // Act
        SessionPersistenceMapper.apply(after, entity);

        // Assert
        assertThat(entity.getBookings()).hasSize(3);
        assertThat(entity.getBookings()).contains(stayingRow);
        assertThat(entity.getBookings()).filteredOn(row -> row.getId().equals(leavingBooking.id().value()))
                .singleElement().satisfies(row -> assertThat(row.getStatus()).isEqualTo(BookingStatus.CANCELLED.name()));
        assertThat(entity.getBookings()).extracting(BookingJpaEntity::getMemberId).contains(newcomer.value());
        assertThat(stayingBooking.status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void aBookingMissingFromTheSessionIsRemovedFromTheEntity() {
        // Arrange
        Session full = bookedBy(MemberId.generate(), MemberId.generate());
        SessionJpaEntity entity = new SessionJpaEntity();
        SessionPersistenceMapper.apply(full, entity);
        Session emptied = SessionFactory.reconstitute(full.id(), full.associationId(), full.trainingGroupId(), full.coachId(),
                full.startsAt(), full.endsAt(), full.capacity(), full.status(), null, List.of(), 0L);

        // Act
        SessionPersistenceMapper.apply(emptied, entity);

        // Assert
        assertThat(entity.getBookings()).isEmpty();
    }

    @Test
    void bookingsAreMappedInRequestTimeThenIdOrderWhateverTheEntityListOrder() {
        // Arrange
        MemberId first = MemberId.generate();
        MemberId second = MemberId.generate();
        MemberId third = MemberId.generate();
        Session session = Fixtures.book(Fixtures.book(Fixtures.book(
                Fixtures.session(AssociationId.generate(), SESSION_START, 5),
                second, 10), first, 10), third, 5);
        SessionJpaEntity entity = new SessionJpaEntity();
        SessionPersistenceMapper.apply(session, entity);
        entity.getBookings().sort(Comparator.comparing(BookingJpaEntity::getId).reversed());

        // Act
        Session mapped = SessionPersistenceMapper.toDomain(entity);

        // Assert
        List<Booking> tied = Stream.of(first, second)
                .map(member -> mapped.bookings().stream().filter(b -> b.memberId().equals(member)).findFirst().orElseThrow())
                .sorted(Comparator.comparing(b -> b.id().value().toString())).toList();
        assertThat(mapped.bookings()).extracting(Booking::memberId)
                .containsExactly(third, tied.get(0).memberId(), tied.get(1).memberId());
    }
}
