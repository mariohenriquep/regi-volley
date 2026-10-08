package com.regivolley.api.domain.factory;

import com.regivolley.api.domain.exception.AtLeastOneLevelRequiredException;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Registers and reconstitutes {@link Association}s together with their {@link Level}s: the only place an association is
 * born. {@link Association}'s constructor checks every invariant, so no path can produce an invalid association.
 * Stateless, so its methods are static.
 *
 * <p>A level added <em>later</em> is created by the association itself ({@code Association.addLevel}), because the rules
 * that span levels are its own; this factory makes the first ones and rebuilds stored ones.
 */
public final class AssociationFactory {

    private AssociationFactory() {
    }

    /**
     * Registers an association (US-01) with a generated id, the default booking, session-generation and no-show policies,
     * its first levels, at version 0.
     *
     * @param nif        optional: null or blank means none; otherwise it must be a valid NIF
     * @param levelNames from the most basic to the most advanced, at least one; the first is the entry level (RN-20)
     *                   until {@code Association.changeEntryLevel} says otherwise
     */
    public static Association create(String name, String shortName, String nif, String locality,
                                     String contactEmail, List<String> levelNames) {
        Objects.requireNonNull(levelNames, "levelNames must not be null");
        AssociationId id = AssociationId.generate();
        List<Level> levels = new ArrayList<>();
        for (String levelName : levelNames) {
            levels.add(new Level(LevelId.generate(), id, levelName, levels.size()));
        }
        if (levels.isEmpty()) {
            throw new AtLeastOneLevelRequiredException();
        }
        return new Association(id, name, ShortName.of(shortName), optionalNif(nif), locality,
                EmailAddress.of(contactEmail), BookingPolicy.defaults(), SessionGenerationPolicy.defaults(),
                NoShowPolicy.defaults(), levels, levels.get(0).id(), 0L);
    }

    /**
     * Rebuilds an association from persisted data. Levels may arrive in any order.
     *
     * @param levels  the association's levels, themselves reconstituted with {@link #reconstituteLevel}
     * @param version the optimistic-lock version it was loaded with (architecture.md section 10)
     */
    public static Association reconstitute(AssociationId id, String name, ShortName shortName, Nif nif,
                                           String locality, EmailAddress contactEmail, BookingPolicy bookingPolicy,
                                           SessionGenerationPolicy sessionGenerationPolicy, NoShowPolicy noShowPolicy,
                                           List<Level> levels, LevelId entryLevelId, long version) {
        return new Association(id, name, shortName, nif, locality, contactEmail, bookingPolicy,
                sessionGenerationPolicy, noShowPolicy, levels, entryLevelId, version);
    }

    /** Rebuilds a level from persisted data, to be handed to {@link #reconstitute} with the rest of its association. */
    public static Level reconstituteLevel(LevelId id, AssociationId associationId, String name, int rank) {
        return new Level(id, associationId, name, rank);
    }

    private static Nif optionalNif(String raw) {
        return raw == null || raw.isBlank() ? null : Nif.of(raw);
    }
}
