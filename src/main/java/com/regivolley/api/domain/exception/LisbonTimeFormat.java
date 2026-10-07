package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.ScheduleZone;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Formats instants for messages: Europe/Lisbon wall-clock time, never raw UTC. */
final class LisbonTimeFormat {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ScheduleZone.LISBON.zoneId());

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private LisbonTimeFormat() {
    }

    static String format(Instant instant) {
        return FORMAT.format(instant);
    }

    /** Calendar dates (subscription periods) are already local, so they are only laid out as dd/MM/yyyy. */
    static String formatDate(LocalDate date) {
        return DATE_FORMAT.format(date);
    }
}
