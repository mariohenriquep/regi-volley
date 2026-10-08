package com.regivolley.api.application.port;

import java.util.function.Supplier;

/**
 * Outbound port for transaction demarcation, so use cases never touch Spring's transaction API
 * (architecture.md section 10). Every call opens a <em>new</em> transaction, independent of any
 * the caller may already be in: a retry after a concurrent-modification conflict must start from
 * a clean one, because the conflicting transaction is rolled back.
 */
public interface TransactionRunner {

    /**
     * Runs {@code work} in a new transaction: committed when it returns, rolled back when it throws
     * (the exception is rethrown unchanged).
     */
    <T> T inNewTransaction(Supplier<T> work);
}
