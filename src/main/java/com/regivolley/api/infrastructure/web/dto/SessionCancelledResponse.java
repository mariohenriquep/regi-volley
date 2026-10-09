package com.regivolley.api.infrastructure.web.dto;

/** The cancelled session, how many bookings it took down and how many credits went back. */
public record SessionCancelledResponse(SessionResponse session, int bookingsCancelled, int creditsRefunded) {
}
