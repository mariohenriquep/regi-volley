package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.DeleteVenueCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.result.VenueDeleted;
import com.regivolley.api.domain.exception.VenueInUseException;
import com.regivolley.api.domain.exception.VenueNotFoundException;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import com.regivolley.api.domain.repository.VenueRepository;
import org.springframework.stereotype.Service;

/**
 * US-02. An administrator deletes a venue, unless an ACTIVE training group runs there
 * ({@link VenueInUseException}); archived groups do not count. The venue's row lock is taken first, and creating a
 * group at a venue takes it too, so a group cannot appear at a venue between the check and the delete: the loser
 * waits, then sees the outcome (a delete retried after a lost race re-checks the groups).
 */
@Service
public class DeleteVenueService implements DeleteVenueUseCase {

    private final MemberRepository members;
    private final VenueRepository venues;
    private final TrainingGroupRepository groups;
    private final UnitOfWork unitOfWork;

    public DeleteVenueService(MemberRepository members, VenueRepository venues, TrainingGroupRepository groups,
                              TransactionRunner transactions) {
        this.members = members;
        this.venues = venues;
        this.groups = groups;
        this.unitOfWork = new UnitOfWork(transactions);
    }

    @Override
    public VenueDeleted execute(DeleteVenueCommand command) {
        return unitOfWork.retrying(() -> {
            var associationId = command.actor().associationId();
            Member admin = Lookups.member(members, associationId, command.actor().memberId());
            Permissions.requireAdmin(admin, "manage venues");
            Venue venue = venues.findByIdForUpdate(associationId, command.venueId())
                    .orElseThrow(() -> new VenueNotFoundException(command.venueId()));
            if (groups.existsActiveWithVenue(associationId, venue.id())) {
                throw new VenueInUseException(venue.id());
            }
            venues.delete(venue);
            return new VenueDeleted(venue.id());
        });
    }
}
