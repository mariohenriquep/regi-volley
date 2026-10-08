package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.EditVenueCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.VenueRepository;
import org.springframework.stereotype.Service;

/** US-02. An administrator edits a venue of their own association; a venue of another association is not found. */
@Service
public class EditVenueService implements EditVenueUseCase {

    private final MemberRepository members;
    private final VenueRepository venues;
    private final UnitOfWork unitOfWork;

    public EditVenueService(MemberRepository members, VenueRepository venues, TransactionRunner transactions) {
        this.members = members;
        this.venues = venues;
        this.unitOfWork = new UnitOfWork(transactions);
    }

    @Override
    public Venue execute(EditVenueCommand command) {
        return unitOfWork.retrying(() -> {
            var associationId = command.actor().associationId();
            Member admin = Lookups.member(members, associationId, command.actor().memberId());
            Permissions.requireAdmin(admin, "manage venues");
            Venue venue = Lookups.venue(venues, associationId, command.venueId());
            return venues.save(venue.edit(command.name(), command.address(), command.courts()));
        });
    }
}
