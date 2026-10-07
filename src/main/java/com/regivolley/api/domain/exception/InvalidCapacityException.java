package com.regivolley.api.domain.exception;

/** Thrown when a capacity change asks for zero or fewer seats (RN-02, US-12). */
public class InvalidCapacityException extends BusinessRuleException {

    private final int requestedCapacity;

    public InvalidCapacityException(int requestedCapacity) {
        super("Capacity must be greater than zero (requested: " + requestedCapacity + ")");
        this.requestedCapacity = requestedCapacity;
    }

    public int requestedCapacity() {
        return requestedCapacity;
    }
}
