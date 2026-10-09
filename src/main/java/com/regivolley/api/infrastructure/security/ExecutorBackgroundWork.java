package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.port.BackgroundWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * {@link BackgroundWork} on small bounded thread pools, one per {@link Lane} so that one lane filling up cannot take another's
 * capacity: {@code ACCOUNT_MAIL} (password-reset work) has 2 threads and 100 queued, {@code ADMIN_RESEND} (an administrator's
 * re-sent activation links) 1 thread and 20 queued. The work runs off the request thread so response time does not reveal whether an
 * address has an account (threat model D-10). When a lane's queue is full the work is dropped and logged, never run on the caller.
 */
public class ExecutorBackgroundWork implements BackgroundWork {

    private static final Logger LOG = LoggerFactory.getLogger(ExecutorBackgroundWork.class);

    private final Map<Lane, ThreadPoolExecutor> lanes = new EnumMap<>(Lane.class);

    public ExecutorBackgroundWork() {
        lanes.put(Lane.ACCOUNT_MAIL, executor(2, 100, "account-mail"));
        lanes.put(Lane.ADMIN_RESEND, executor(1, 20, "account-resend"));
    }

    private static ThreadPoolExecutor executor(int threads, int queue, String name) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(queue), runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        });
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    @Override
    public void run(Lane lane, Runnable work) {
        try {
            lanes.get(lane).execute(work);
        } catch (RejectedExecutionException e) {
            LOG.warn("Background account work dropped: the {} lane is full", lane);
        }
    }

    /** Stops accepting work (application shutdown). */
    public void shutdown() {
        lanes.values().forEach(ThreadPoolExecutor::shutdown);
    }
}
