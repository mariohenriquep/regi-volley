package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.CreateVenueCommand;
import com.regivolley.api.domain.model.entity.Venue;

/** US-02: an administrator adds a venue. */
public interface CreateVenueUseCase extends UseCase<CreateVenueCommand, Venue> {
}
