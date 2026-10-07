package com.regivolley.api.application.usecase;

import com.regivolley.api.application.port.TransactionRunner;

import java.util.function.Supplier;

/** A {@link TransactionRunner} for unit tests: runs the work as is and counts how many transactions were opened. */
final class DirectTransactions implements TransactionRunner {

    private int opened;

    @Override
    public <T> T inNewTransaction(Supplier<T> work) {
        opened++;
        return work.get();
    }

    int opened() {
        return opened;
    }
}
