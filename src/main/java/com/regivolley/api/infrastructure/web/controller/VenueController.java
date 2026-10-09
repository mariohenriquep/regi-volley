package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.usecase.CreateVenueUseCase;
import com.regivolley.api.application.usecase.DeleteVenueUseCase;
import com.regivolley.api.application.usecase.EditVenueUseCase;
import com.regivolley.api.infrastructure.security.AuthenticatedActor;
import com.regivolley.api.infrastructure.security.CurrentActor;
import com.regivolley.api.infrastructure.web.dto.VenueRequest;
import com.regivolley.api.infrastructure.web.dto.VenueResponse;
import com.regivolley.api.infrastructure.web.mapper.VenueWebMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The venues of the caller's association (US-02), for administrators (the use cases check). */
@RestController
@RequestMapping("/api/v1/venues")
public class VenueController {

    private final CreateVenueUseCase createVenue;
    private final EditVenueUseCase editVenue;
    private final DeleteVenueUseCase deleteVenue;

    public VenueController(CreateVenueUseCase createVenue, EditVenueUseCase editVenue, DeleteVenueUseCase deleteVenue) {
        this.createVenue = createVenue;
        this.editVenue = editVenue;
        this.deleteVenue = deleteVenue;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VenueResponse create(@CurrentActor AuthenticatedActor caller, @Valid @RequestBody VenueRequest body) {
        return VenueWebMapper.toResponse(createVenue.execute(VenueWebMapper.createCommand(caller.actor(), body)));
    }

    @PutMapping("/{venueId}")
    public VenueResponse edit(@CurrentActor AuthenticatedActor caller, @PathVariable UUID venueId, @Valid @RequestBody VenueRequest body) {
        return VenueWebMapper.toResponse(editVenue.execute(VenueWebMapper.editCommand(caller.actor(), venueId, body)));
    }

    @DeleteMapping("/{venueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentActor AuthenticatedActor caller, @PathVariable UUID venueId) {
        deleteVenue.execute(VenueWebMapper.deleteCommand(caller.actor(), venueId));
    }
}
