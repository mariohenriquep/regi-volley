package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListBookableSessionsQuery;
import com.regivolley.api.application.result.BookableSession;

import java.util.List;

/** US-13: the week's sessions a member may book, with seats, waitlist and their own status. */
public interface ListBookableSessionsUseCase extends UseCase<ListBookableSessionsQuery, List<BookableSession>> {
}
