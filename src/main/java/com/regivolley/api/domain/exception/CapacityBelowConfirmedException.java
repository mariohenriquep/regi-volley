package com.regivolley.api.domain.exception;

/** Thrown when a session's capacity would drop below its number of CONFIRMED bookings (RN-02, US-12). */
public class CapacityBelowConfirmedException extends BusinessRuleException {

    private final int requestedCapacity;
    private final int confirmedCount;

    public CapacityBelowConfirmedException(int requestedCapacity, int confirmedCount) {
        super("Capacity (" + requestedCapacity + ") cannot be lower than the number of confirmed bookings ("
                + confirmedCount + ")");
        this.requestedCapacity = requestedCapacity;
        this.confirmedCount = confirmedCount;
    }

    public int requestedCapacity() {
        return requestedCapacity;
    }

    public int confirmedCount() {
        return confirmedCount;
    }
}
