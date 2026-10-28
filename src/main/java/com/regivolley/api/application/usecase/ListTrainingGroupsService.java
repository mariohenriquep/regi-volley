package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListTrainingGroupsQuery;
import com.regivolley.api.application.result.TrainingGroupListEntry;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Level;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import com.regivolley.api.domain.repository.VenueRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * US-09. An administrator lists the training groups of their association: the ACTIVE ones, in the repository's order (by name), and
 * with {@code includeArchived} the ARCHIVED ones after them. Each comes with the names an administrator reads beside its ids: the
 * accepted levels (lowest first, from the association's one load), the venue (one query for the association's venues) and the coach
 * (one batch). A name that cannot be found is left out, not a failure. Read only, so no transaction of its own.
 */
@Service
public class ListTrainingGroupsService implements ListTrainingGroupsUseCase {

    private final MemberRepository members;
    private final AssociationRepository associations;
    private final TrainingGroupRepository groups;
    private final VenueRepository venues;

    public ListTrainingGroupsService(MemberRepository members, AssociationRepository associations, TrainingGroupRepository groups,
                                     VenueRepository venues) {
        this.members = members;
        this.associations = associations;
        this.groups = groups;
        this.venues = venues;
    }

    @Override
    public List<TrainingGroupListEntry> execute(ListTrainingGroupsQuery query) {
        var associationId = query.actor().associationId();
        Member admin = Lookups.member(members, associationId, query.actor().memberId());
        Permissions.requireAdmin(admin, "list training groups");

        List<TrainingGroup> shown = groups.findAllByAssociation(associationId).stream()
                .filter(group -> query.includeArchived() || group.isActive())
                .sorted(Comparator.comparing((TrainingGroup group) -> !group.isActive()))
                .toList();
        if (shown.isEmpty()) {
            return List.of();
        }
        Association association = Lookups.association(associations, associationId);
        Map<VenueId, String> venueNames = Names.ofVenues(venues, associationId);
        Set<MemberId> coachIds = shown.stream().map(TrainingGroup::coachId).collect(Collectors.toSet());
        Map<MemberId, String> coachNames = members.findByIds(associationId, coachIds).stream()
                .collect(Collectors.toMap(Member::id, Member::name));
        return shown.stream().map(group -> new TrainingGroupListEntry(group,
                association.levels().stream().filter(level -> group.acceptedLevels().contains(level.id()))
                        .sorted(Comparator.comparingInt(Level::rank)).toList(),
                Optional.ofNullable(venueNames.get(group.venueId())), Optional.ofNullable(coachNames.get(group.coachId())))).toList();
    }
}
