package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

import java.time.ZoneId;

/**
 * The time zone in which recurring schedules and deadlines are expressed (architecture.md
 * section 9). Instants are stored in UTC; anything that speaks of wall-clock time ("20:00",
 * "7 days before", a calendar date) goes through this zone, so it stays correct across DST.
 */
public enum ScheduleZone implements ValueObject {
    LISBON(ZoneId.of("Europe/Lisbon"));

    private final ZoneId zoneId;

    ScheduleZone(ZoneId zoneId) {
        this.zoneId = zoneId;
    }

    public ZoneId zoneId() {
        return zoneId;
    }
}
