package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListVenuesQuery;
import com.regivolley.api.application.result.VenueListEntry;

import java.util.List;

/** US-02: an administrator lists the association's venues. */
public interface ListVenuesUseCase extends UseCase<ListVenuesQuery, List<VenueListEntry>> {
}
