package com.regivolley.api.infrastructure.notification;

import com.regivolley.api.domain.model.valueobject.ScheduleZone;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/** How a moment is written in an email: {@code dd/MM/yyyy HH:mm} in Lisbon time, like the messages of the API (architecture.md section 12). */
final class MailFormats {

    private static final DateTimeFormatter LISBON = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ScheduleZone.LISBON.zoneId());

    private MailFormats() {
    }

    static String lisbon(Instant instant) {
        return LISBON.format(instant);
    }
}
