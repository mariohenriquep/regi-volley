package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListVenuesQuery;
import com.regivolley.api.application.result.VenueListEntry;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import com.regivolley.api.domain.repository.VenueRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * US-02. An administrator lists the venues of their association, by name as the repository gives them, each with whether an ACTIVE
 * training group runs there (such a venue cannot be deleted, {@code DeleteVenueService} decides that again on submit). The groups
 * are read once for all the venues, not once per venue. Read only, so no transaction of its own.
 */
@Service
public class ListVenuesService implements ListVenuesUseCase {

    private final MemberRepository members;
    private final VenueRepository venues;
    private final TrainingGroupRepository groups;

    public ListVenuesService(MemberRepository members, VenueRepository venues, TrainingGroupRepository groups) {
        this.members = members;
        this.venues = venues;
        this.groups = groups;
    }

    @Override
    public List<VenueListEntry> execute(ListVenuesQuery query) {
        var associationId = query.actor().associationId();
        Member admin = Lookups.member(members, associationId, query.actor().memberId());
        Permissions.requireAdmin(admin, "list venues");
        List<Venue> found = venues.findAllByAssociation(associationId);
        if (found.isEmpty()) {
            return List.of();
        }
        Set<VenueId> inUse = groups.findAllByAssociation(associationId).stream()
                .filter(TrainingGroup::isActive).map(TrainingGroup::venueId).collect(Collectors.toSet());
        return found.stream().map(venue -> new VenueListEntry(venue, inUse.contains(venue.id()))).toList();
    }
}
