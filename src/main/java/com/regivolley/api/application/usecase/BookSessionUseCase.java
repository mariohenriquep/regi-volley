package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.BookSessionCommand;
import com.regivolley.api.application.result.PlacedBooking;

/** US-14: a member books a place in a session. */
public interface BookSessionUseCase extends UseCase<BookSessionCommand, PlacedBooking> {
}
