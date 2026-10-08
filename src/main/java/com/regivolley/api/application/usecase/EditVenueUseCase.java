package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.EditVenueCommand;
import com.regivolley.api.domain.model.entity.Venue;

/** US-02: an administrator edits a venue. */
public interface EditVenueUseCase extends UseCase<EditVenueCommand, Venue> {
}
