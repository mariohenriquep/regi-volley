package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.GetPublicAssociationQuery;
import com.regivolley.api.application.result.PublicAssociationPage;
import com.regivolley.api.application.result.PublicGroup;
import com.regivolley.api.application.result.PublicLevel;
import com.regivolley.api.application.result.PublicSlot;
import com.regivolley.api.application.result.PublicVenue;
import com.regivolley.api.domain.exception.ShortNameNotFoundException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Level;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.ShortName;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.model.valueobject.WeeklySlot;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import com.regivolley.api.domain.repository.VenueRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * US-24. The public page of the association with a given short name - the one place a tenant is chosen by something the
 * caller supplies (architecture.md section 8), and only for data the association publishes: levels (lowest first), the active
 * groups with their weekly schedule and venue, the venues and the contact address. The page is built as an explicit
 * whitelist ({@link PublicAssociationPage}); the groups and venues are read for the association the short name resolved to,
 * never for another. Read only, so no transaction of its own.
 */
@Service
public class GetPublicAssociationService implements GetPublicAssociationUseCase {

    private final AssociationRepository associations;
    private final TrainingGroupRepository groups;
    private final VenueRepository venues;

    public GetPublicAssociationService(AssociationRepository associations, TrainingGroupRepository groups, VenueRepository venues) {
        this.associations = associations;
        this.groups = groups;
        this.venues = venues;
    }

    @Override
    public PublicAssociationPage execute(GetPublicAssociationQuery query) {
        ShortName shortName = ShortName.tryOf(query.shortName()).orElseThrow(() -> new ShortNameNotFoundException(query.shortName()));
        Association association = associations.findByShortName(shortName).orElseThrow(() -> new ShortNameNotFoundException(shortName));
        List<Venue> venuesOfAssociation = venues.findAllByAssociation(association.id());
        Map<VenueId, Venue> venueById = venuesOfAssociation.stream().collect(Collectors.toMap(Venue::id, Function.identity()));

        List<PublicLevel> levels = association.levels().stream().sorted(Comparator.comparingInt(Level::rank))
                .map(level -> new PublicLevel(level.name())).toList();
        Map<LevelId, Level> levelById = association.levels().stream().collect(Collectors.toMap(Level::id, Function.identity()));
        List<PublicGroup> publicGroups = groups.findAllByAssociation(association.id()).stream()
                .filter(TrainingGroup::isActive)
                .map(group -> publicGroup(group, levelById, venueById))
                .toList();
        List<PublicVenue> publicVenues = venuesOfAssociation.stream()
                .map(venue -> new PublicVenue(venue.name(), venue.address(), venue.courts())).toList();
        return new PublicAssociationPage(association.name(), association.shortName().value(), association.locality(),
                association.contactEmail().value(), levels, publicGroups, publicVenues);
    }

    private static PublicGroup publicGroup(TrainingGroup group, Map<LevelId, Level> levelById, Map<VenueId, Venue> venueById) {
        Venue venue = venueById.get(group.venueId());
        List<String> levelNames = group.acceptedLevels().stream().map(levelById::get).filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(Level::rank)).map(Level::name).toList();
        List<PublicSlot> schedule = group.schedule().slots().stream().map(GetPublicAssociationService::slot).toList();
        return new PublicGroup(group.name(), levelNames, venue == null ? null : venue.name(), schedule);
    }

    private static PublicSlot slot(WeeklySlot slot) {
        return new PublicSlot(slot.dayOfWeek(), slot.startTime(), (int) slot.duration().toMinutes());
    }
}
