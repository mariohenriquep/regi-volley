package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.CreateVenueCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.domain.factory.VenueFactory;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.VenueRepository;
import org.springframework.stereotype.Service;

/** US-02. An administrator adds a venue (name, address, number of courts) to their own association. */
@Service
public class CreateVenueService implements CreateVenueUseCase {

    private final MemberRepository members;
    private final VenueRepository venues;
    private final UnitOfWork unitOfWork;

    public CreateVenueService(MemberRepository members, VenueRepository venues, TransactionRunner transactions) {
        this.members = members;
        this.venues = venues;
        this.unitOfWork = new UnitOfWork(transactions);
    }

    @Override
    public Venue execute(CreateVenueCommand command) {
        var associationId = command.actor().associationId();
        Venue venue = VenueFactory.create(associationId, command.name(), command.address(), command.courts());
        return unitOfWork.retrying(() -> {
            Member admin = Lookups.member(members, associationId, command.actor().memberId());
            Permissions.requireAdmin(admin, "manage venues");
            return venues.save(venue);
        });
    }
}
