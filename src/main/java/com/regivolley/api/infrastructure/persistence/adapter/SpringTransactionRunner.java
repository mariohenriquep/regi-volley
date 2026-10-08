package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.application.port.TransactionRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * {@link TransactionRunner} on Spring's {@link TransactionTemplate}. Every call is
 * {@code REQUIRES_NEW}: a use case retrying after a concurrent-modification conflict must not join a
 * transaction that is already doomed (architecture.md section 10). A RuntimeException rolls back and is
 * rethrown unchanged, so the typed conflict reaches the retry loop.
 */
@Component
public class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate template;

    public SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
        this.template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public <T> T inNewTransaction(Supplier<T> work) {
        return template.execute(status -> work.get());
    }
}
