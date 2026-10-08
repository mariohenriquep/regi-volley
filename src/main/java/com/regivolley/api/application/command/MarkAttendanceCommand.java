package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.SessionId;

import java.util.List;
import java.util.Objects;

/**
 * Marks present / no-show for several bookings of one session at once (US-17), all or nothing. The
 * actor must coach the session or be an administrator.
 */
public record MarkAttendanceCommand(Actor actor, SessionId sessionId, List<AttendanceEntry> entries) {

    public MarkAttendanceCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        entries = List.copyOf(entries);
    }
}
