package com.regivolley.api.infrastructure.persistence.mapper;

import com.regivolley.api.domain.factory.AssociationFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Level;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.BookingPolicy;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.Nif;
import com.regivolley.api.domain.model.valueobject.NoShowPolicy;
import com.regivolley.api.domain.model.valueobject.SessionGenerationPolicy;
import com.regivolley.api.domain.model.valueobject.ShortName;
import com.regivolley.api.infrastructure.persistence.entity.AssociationJpaEntity;
import com.regivolley.api.infrastructure.persistence.entity.LevelJpaEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Translates an {@link Association} and its {@link Level}s to and from {@link AssociationJpaEntity} / {@link LevelJpaEntity}. */
public final class AssociationPersistenceMapper {

    private AssociationPersistenceMapper() {
    }

    /** Rebuilds the aggregate through its factory, which re-checks its invariants. The entity's levels must be initialised. */
    public static Association toDomain(AssociationJpaEntity entity) {
        AssociationId id = new AssociationId(entity.getId());
        List<Level> levels = entity.getLevels().stream()
                .map(level -> AssociationFactory.reconstituteLevel(new LevelId(level.getId()), id, level.getName(), level.getLevelRank()))
                .toList();
        return AssociationFactory.reconstitute(
                id,
                entity.getName(),
                new ShortName(entity.getShortName()),
                entity.getNif() == null ? null : new Nif(entity.getNif()),
                entity.getLocality(),
                new EmailAddress(entity.getContactEmail()),
                new BookingPolicy(entity.getBookingWindowDays(), entity.getFreeCancellationHours()),
                new SessionGenerationPolicy(entity.getSessionGenerationWeeks()),
                new NoShowPolicy(entity.getNoShowLimit()),
                levels,
                new LevelId(entity.getEntryLevelId()),
                entity.getVersion() == null ? 0L : entity.getVersion());
    }

    /**
     * Copies the association onto {@code entity} (new, or loaded and managed) and syncs its levels:
     * existing rows are updated in place, new ones added, vanished ones removed. The version stays
     * with the persistence layer.
     */
    public static void apply(Association association, AssociationJpaEntity entity) {
        entity.setId(association.id().value());
        entity.setName(association.name());
        entity.setShortName(association.shortName().value());
        entity.setNif(association.nif().map(Nif::value).orElse(null));
        entity.setLocality(association.locality());
        entity.setContactEmail(association.contactEmail().value());
        entity.setBookingWindowDays(association.bookingPolicy().bookingWindowDays());
        entity.setFreeCancellationHours(association.bookingPolicy().freeCancellationHours());
        entity.setSessionGenerationWeeks(association.sessionGenerationPolicy().windowWeeks());
        entity.setNoShowLimit(association.noShowPolicy().monthlyLimit());
        entity.setEntryLevelId(association.entryLevelId().value());
        syncLevels(association.levels(), entity);
    }

    private static void syncLevels(List<Level> levels, AssociationJpaEntity entity) {
        Map<UUID, LevelJpaEntity> existing = new HashMap<>();
        entity.getLevels().forEach(level -> existing.put(level.getId(), level));
        List<UUID> wanted = levels.stream().map(level -> level.id().value()).toList();
        entity.getLevels().removeIf(level -> !wanted.contains(level.getId()));
        for (Level level : levels) {
            LevelJpaEntity row = existing.get(level.id().value());
            if (row == null) {
                row = new LevelJpaEntity();
                row.setId(level.id().value());
                row.setAssociation(entity);
                entity.getLevels().add(row);
            }
            row.setName(level.name());
            row.setLevelRank(level.rank());
        }
    }
}
