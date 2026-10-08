package com.regivolley.api.application.usecase;

import com.regivolley.api.domain.port.Notifier;

import java.util.List;
import java.util.function.Consumer;

/**
 * What one transactional attempt of a use case produced: the result for the caller and the
 * notifications to send once, and only once, the transaction has committed.
 */
record Outcome<R>(R result, List<Consumer<Notifier>> notifications) {

    Outcome {
        notifications = List.copyOf(notifications);
    }

    static <R> Outcome<R> of(R result, List<Consumer<Notifier>> notifications) {
        return new Outcome<>(result, notifications);
    }

    static <R> Outcome<R> silent(R result) {
        return new Outcome<>(result, List.of());
    }
}
