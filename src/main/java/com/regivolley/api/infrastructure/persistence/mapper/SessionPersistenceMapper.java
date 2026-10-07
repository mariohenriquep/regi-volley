package com.regivolley.api.infrastructure.persistence.mapper;

import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.CancellationKind;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.model.valueobject.SessionStatus;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import com.regivolley.api.infrastructure.persistence.entity.BookingJpaEntity;
import com.regivolley.api.infrastructure.persistence.entity.SessionJpaEntity;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Translates a {@link Session} and its {@link Booking}s to and from {@link SessionJpaEntity} /
 * {@link BookingJpaEntity}. Bookings are mapped only here, as part of their session.
 */
public final class SessionPersistenceMapper {

    /** The waitlist order of architecture.md section 10: requested_at, then id (Postgres uuid order = canonical text order). */
    private static final Comparator<BookingJpaEntity> ARRIVAL = Comparator
            .comparing(BookingJpaEntity::getRequestedAt)
            .thenComparing(booking -> booking.getId().toString());

    private SessionPersistenceMapper() {
    }

    /** Rebuilds the aggregate, re-checking its invariants. The entity's bookings must be initialised. */
    public static Session toDomain(SessionJpaEntity entity) {
        List<Booking> bookings = entity.getBookings().stream()
                .sorted(ARRIVAL)
                .map(SessionPersistenceMapper::toDomain)
                .toList();
        return Session.reconstruct(
                new SessionId(entity.getId()),
                new AssociationId(entity.getAssociationId()),
                new TrainingGroupId(entity.getTrainingGroupId()),
                new MemberId(entity.getCoachId()),
                entity.getStartsAt(),
                entity.getEndsAt(),
                entity.getCapacity(),
                SessionStatus.valueOf(entity.getStatus()),
                entity.getCancellationReason(),
                bookings,
                entity.getVersion() == null ? 0L : entity.getVersion());
    }

    private static Booking toDomain(BookingJpaEntity entity) {
        return Booking.reconstruct(
                new BookingId(entity.getId()),
                new AssociationId(entity.getAssociationId()),
                new SessionId(entity.getSession().getId()),
                new MemberId(entity.getMemberId()),
                BookingStatus.valueOf(entity.getStatus()),
                entity.getRequestedAt(),
                entity.getConfirmedAt(),
                entity.getCancellationKind() == null ? null : CancellationKind.valueOf(entity.getCancellationKind()));
    }

    /**
     * Copies the session onto {@code entity} (new, or loaded and managed) and syncs its bookings:
     * existing rows are updated in place, new ones added, vanished ones removed. The version is not
     * copied: it belongs to the persistence layer, which compares it before calling this.
     */
    public static void apply(Session session, SessionJpaEntity entity) {
        entity.setId(session.id().value());
        entity.setAssociationId(session.associationId().value());
        entity.setTrainingGroupId(session.trainingGroupId().value());
        entity.setCoachId(session.coachId().value());
        entity.setStartsAt(session.startsAt());
        entity.setEndsAt(session.endsAt());
        entity.setCapacity(session.capacity());
        entity.setStatus(session.status().name());
        entity.setCancellationReason(session.cancellationReason().orElse(null));
        syncBookings(session.bookings(), entity);
    }

    private static void syncBookings(List<Booking> bookings, SessionJpaEntity entity) {
        Map<UUID, BookingJpaEntity> existing = new HashMap<>();
        entity.getBookings().forEach(booking -> existing.put(booking.getId(), booking));
        List<UUID> wanted = bookings.stream().map(booking -> booking.id().value()).toList();
        entity.getBookings().removeIf(booking -> !wanted.contains(booking.getId()));
        for (Booking booking : bookings) {
            BookingJpaEntity row = existing.get(booking.id().value());
            if (row == null) {
                row = new BookingJpaEntity();
                row.setId(booking.id().value());
                row.setSession(entity);
                entity.getBookings().add(row);
            }
            row.setAssociationId(booking.associationId().value());
            row.setMemberId(booking.memberId().value());
            row.setStatus(booking.status().name());
            row.setRequestedAt(booking.requestedAt());
            row.setConfirmedAt(booking.confirmedAt().orElse(null));
            row.setCancellationKind(booking.cancellationKind().map(Enum::name).orElse(null));
        }
    }
}
