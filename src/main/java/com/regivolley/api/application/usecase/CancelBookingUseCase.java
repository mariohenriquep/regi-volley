package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.CancelBookingCommand;
import com.regivolley.api.application.result.CancelledBooking;

/** US-15: cancel a booking, with refund and waitlist promotion. */
public interface CancelBookingUseCase extends UseCase<CancelBookingCommand, CancelledBooking> {
}
