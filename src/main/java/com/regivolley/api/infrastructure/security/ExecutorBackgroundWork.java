package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.port.BackgroundWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * {@link BackgroundWork} on a small bounded thread pool (2 threads, 100 queued): the password-reset work runs off the request
 * thread so response time does not reveal whether an address has an account (threat model D-10). When the queue is full the work
 * is dropped and logged, never run on the caller's thread.
 */
public class ExecutorBackgroundWork implements BackgroundWork {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutorBackgroundWork.class);
    private static final int THREADS = 2;
    private static final int QUEUE = 100;

    private final ThreadPoolExecutor executor;

    public ExecutorBackgroundWork() {
        this.executor = new ThreadPoolExecutor(THREADS, THREADS, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(QUEUE), runnable -> {
            Thread thread = new Thread(runnable, "account-mail");
            thread.setDaemon(true);
            return thread;
        });
        executor.allowCoreThreadTimeOut(true);
    }

    @Override
    public void run(Runnable work) {
        try {
            executor.execute(work);
        } catch (RejectedExecutionException e) {
            LOG.warn("Background account work dropped: the queue is full");
        }
    }

    /** Stops accepting work (application shutdown). */
    public void shutdown() {
        executor.shutdown();
    }
}
