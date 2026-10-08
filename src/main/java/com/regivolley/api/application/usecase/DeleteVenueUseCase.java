package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.DeleteVenueCommand;
import com.regivolley.api.application.result.VenueDeleted;

/** US-02: an administrator deletes a venue no active group uses. */
public interface DeleteVenueUseCase extends UseCase<DeleteVenueCommand, VenueDeleted> {
}
