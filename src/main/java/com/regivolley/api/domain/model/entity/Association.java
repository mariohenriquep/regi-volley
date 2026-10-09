package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.DuplicateLevelNameException;
import com.regivolley.api.domain.exception.InvalidAssociationException;
import com.regivolley.api.domain.exception.LevelNotFoundException;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.BookingPolicy;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.LevelRank;
import com.regivolley.api.domain.model.valueobject.Nif;
import com.regivolley.api.domain.model.valueobject.NoShowPolicy;
import com.regivolley.api.domain.model.valueobject.SessionGenerationPolicy;
import com.regivolley.api.domain.model.valueobject.ShortName;
import com.regivolley.api.domain.shared.AggregateRoot;
import com.regivolley.api.domain.shared.FieldRules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The tenant (US-01, US-03): a volleyball association with its public identity, its default
 * {@link BookingPolicy} and its ordered {@link Level}s. Immutable: every change returns a new
 * instance.
 *
 * <p><b>Why levels live inside the association.</b> The rules of US-03 span all levels at once
 * (at least one, exactly one entry level, a total order, no duplicate names), and reordering
 * touches every level, so they can only be enforced by one aggregate that owns the whole list.
 * The entry level (RN-20) is a single {@code entryLevelId} rather than a flag on each level, so
 * "exactly one" holds by construction and can't be broken by a bad update. It need not be the
 * lowest level.
 *
 * <p><b>Ranks</b> are the positions in the ordered list: 0 is the most basic level, n-1 the most
 * advanced, with no gaps. They feed booking eligibility as {@link LevelRank}.
 *
 * <p>The short name is immutable (it is the public URL) and unique across associations; that
 * uniqueness is the registration use case's job, through a repository. Levels can be added,
 * renamed and reordered here; removing one needs to know which members and groups use it, so it
 * is left to a later slice.
 *
 * <p>An association is registered and reconstituted only by {@code AssociationFactory}; the association itself
 * creates the levels added later ({@link #addLevel}), because it guards the rules that span them.
 */
public final class Association implements AggregateRoot {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_LOCALITY_LENGTH = 100;

    private final AssociationId id;
    private final String name;
    private final ShortName shortName;
    private final Nif nif;
    private final String locality;
    private final EmailAddress contactEmail;
    private final BookingPolicy bookingPolicy;
    private final SessionGenerationPolicy sessionGenerationPolicy;
    private final NoShowPolicy noShowPolicy;
    private final List<Level> levels;
    private final LevelId entryLevelId;
    private final long version;

    /**
     * Checks every invariant, so no association exists in an invalid state. Public because the only callers are
     * {@code AssociationFactory} (registration and persisted associations) and this class; the architecture test
     * pins that. Levels may arrive in any order; they are kept sorted by rank.
     *
     * @param version the optimistic-lock version it was loaded with (0 for a new association); edits carry it
     *                over unchanged, so a stale copy is detected when it is saved (architecture.md section 10)
     */
    public Association(AssociationId id, String name, ShortName shortName, Nif nif, String locality,
                       EmailAddress contactEmail, BookingPolicy bookingPolicy,
                       SessionGenerationPolicy sessionGenerationPolicy, NoShowPolicy noShowPolicy,
                       List<Level> levels, LevelId entryLevelId, long version) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(levels, "levels must not be null");
        Objects.requireNonNull(entryLevelId, "entryLevelId must not be null");
        if (version < 0) {
            throw new InvalidAssociationException("The version must not be negative");
        }
        List<Level> ordered = levels.stream().sorted(Comparator.comparingInt(Level::rank)).toList();
        requireValidLevels(id, ordered, entryLevelId);
        this.id = id;
        this.name = FieldRules.requiredText("association name", name, MAX_NAME_LENGTH);
        this.shortName = Objects.requireNonNull(shortName, "shortName must not be null");
        this.nif = nif;
        this.locality = FieldRules.requiredText("locality", locality, MAX_LOCALITY_LENGTH);
        this.contactEmail = Objects.requireNonNull(contactEmail, "contactEmail must not be null");
        this.bookingPolicy = Objects.requireNonNull(bookingPolicy, "bookingPolicy must not be null");
        this.sessionGenerationPolicy = Objects.requireNonNull(sessionGenerationPolicy, "sessionGenerationPolicy must not be null");
        this.noShowPolicy = Objects.requireNonNull(noShowPolicy, "noShowPolicy must not be null");
        this.levels = ordered;
        this.entryLevelId = entryLevelId;
        this.version = version;
    }

    private static void requireValidLevels(AssociationId id, List<Level> ordered, LevelId entryLevelId) {
        if (ordered.isEmpty()) {
            throw new InvalidAssociationException("An association needs at least one level");
        }
        Set<LevelId> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (int position = 0; position < ordered.size(); position++) {
            Level level = ordered.get(position);
            if (!level.associationId().equals(id)) {
                throw new InvalidAssociationException("A level belongs to another association");
            }
            if (level.rank() != position) {
                throw new InvalidAssociationException("Level ranks must run from 0 to " + (ordered.size() - 1)
                        + " without gaps or repeats");
            }
            if (!ids.add(level.id())) {
                throw new InvalidAssociationException("A level appears twice: " + level.id());
            }
            if (!names.add(normalised(level.name()))) {
                throw new DuplicateLevelNameException(level.name());
            }
        }
        if (!ids.contains(entryLevelId)) {
            throw new InvalidAssociationException("The entry level must be one of the association's levels");
        }
    }

    private static Nif optionalNif(String raw) {
        return raw == null || raw.isBlank() ? null : Nif.of(raw);
    }

    private static String normalised(String levelName) {
        return levelName.trim().toLowerCase(Locale.ROOT);
    }

    /** Edits the details an administrator may change; the short name stays (it is the public URL). */
    public Association updateDetails(String newName, String newNif, String newLocality, String newContactEmail) {
        return new Association(id, newName, shortName, optionalNif(newNif), newLocality,
                EmailAddress.of(newContactEmail), bookingPolicy, sessionGenerationPolicy, noShowPolicy, levels, entryLevelId,
                version);
    }

    public Association changeBookingPolicy(BookingPolicy newPolicy) {
        return new Association(id, name, shortName, nif, locality, contactEmail, newPolicy, sessionGenerationPolicy,
                noShowPolicy, levels, entryLevelId, version);
    }

    /** RN-11: how many no-shows in a month trigger the warning to the member and the administrators. */
    public Association changeNoShowPolicy(NoShowPolicy newPolicy) {
        return new Association(id, name, shortName, nif, locality, contactEmail, bookingPolicy, sessionGenerationPolicy,
                newPolicy, levels, entryLevelId, version);
    }

    /** Adds a level as the most advanced one; reorder afterwards to place it elsewhere. */
    public Association addLevel(String levelName) {
        List<Level> updated = new ArrayList<>(levels);
        updated.add(new Level(LevelId.generate(), id, levelName, levels.size()));
        return withLevels(updated, entryLevelId);
    }

    public Association renameLevel(LevelId levelId, String newName) {
        requireLevel(levelId);
        List<Level> updated = levels.stream()
                .map(level -> level.id().equals(levelId) ? level.renamedTo(newName) : level)
                .toList();
        return withLevels(updated, entryLevelId);
    }

    /**
     * Sets the order of all levels, most basic first (US-03).
     *
     * @throws InvalidAssociationException unless {@code orderedLevelIds} lists every level exactly once
     */
    public Association reorderLevels(List<LevelId> orderedLevelIds) {
        Objects.requireNonNull(orderedLevelIds, "orderedLevelIds must not be null");
        if (orderedLevelIds.size() != levels.size() || !new HashSet<>(orderedLevelIds).equals(levelIds())) {
            throw new InvalidAssociationException("Reordering must list every level exactly once");
        }
        List<Level> updated = new ArrayList<>();
        for (int position = 0; position < orderedLevelIds.size(); position++) {
            updated.add(level(orderedLevelIds.get(position)).withRank(position));
        }
        return withLevels(updated, entryLevelId);
    }

    /** Makes another level the entry level (RN-20); the previous one stops being it. */
    public Association changeEntryLevel(LevelId levelId) {
        requireLevel(levelId);
        return withLevels(levels, levelId);
    }

    private Association withLevels(List<Level> newLevels, LevelId newEntryLevelId) {
        return new Association(id, name, shortName, nif, locality, contactEmail, bookingPolicy, sessionGenerationPolicy,
                noShowPolicy, newLevels, newEntryLevelId, version);
    }

    private void requireLevel(LevelId levelId) {
        if (!levelIds().contains(levelId)) {
            throw new LevelNotFoundException(levelId);
        }
    }

    private Set<LevelId> levelIds() {
        Set<LevelId> ids = new HashSet<>();
        levels.forEach(level -> ids.add(level.id()));
        return ids;
    }

    /** @throws LevelNotFoundException if the level isn't one of this association's */
    public Level level(LevelId levelId) {
        return levels.stream()
                .filter(level -> level.id().equals(levelId))
                .findFirst()
                .orElseThrow(() -> new LevelNotFoundException(levelId));
    }

    /** The level a new member starts at (RN-20). */
    public Level entryLevel() {
        return level(entryLevelId);
    }

    /** What booking eligibility needs of one level (RN-14, RN-21). */
    public LevelRank rankOf(LevelId levelId) {
        return level(levelId).toRank();
    }

    /** What booking eligibility needs of several levels, e.g. those a group accepts (RN-21). */
    public Set<LevelRank> ranksOf(Set<LevelId> levelIds) {
        return levelIds.stream().map(this::rankOf).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Every one of these levels must be one of this association's (a group or a plan may not use another tenant's).
     *
     * @throws LevelNotFoundException for a level that is not
     */
    public void requireLevels(Set<LevelId> levelIds) {
        levelIds.forEach(this::level);
    }

    /** Whether every one of these levels is one of this association's (a group may not accept another tenant's level). */
    public boolean hasAllLevels(Set<LevelId> levelIds) {
        return levelIds().containsAll(levelIds);
    }

    /** All levels, most basic first. */
    public List<Level> levels() {
        return levels;
    }

    public AssociationId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public ShortName shortName() {
        return shortName;
    }

    public Optional<Nif> nif() {
        return Optional.ofNullable(nif);
    }

    public String locality() {
        return locality;
    }

    public EmailAddress contactEmail() {
        return contactEmail;
    }

    /** The association's default booking timing rules (RN-03, RN-10). */
    public BookingPolicy bookingPolicy() {
        return bookingPolicy;
    }

    /**
     * How far ahead this association's sessions are generated (RN-01); 4 weeks unless changed. Not
     * editable yet: when it becomes so, an out-of-range value must be a BusinessRuleException
     * (architecture.md section 12), not the IllegalArgumentException the value object throws today.
     */
    public SessionGenerationPolicy sessionGenerationPolicy() {
        return sessionGenerationPolicy;
    }

    /** When reaching a monthly no-show count warns the member and administrators (RN-11); 3 unless changed. */
    public NoShowPolicy noShowPolicy() {
        return noShowPolicy;
    }

    public LevelId entryLevelId() {
        return entryLevelId;
    }

    /** Optimistic-lock version: concurrent edits of the association (details, policy, levels) must not be lost. */
    public long version() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Association other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Association{id=%s, shortName=%s}".formatted(id, shortName);
    }
}
