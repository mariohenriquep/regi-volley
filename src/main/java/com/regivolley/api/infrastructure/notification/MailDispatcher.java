package com.regivolley.api.infrastructure.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs mail work off the request thread, on a small fixed pool, with a bound and a retry policy (issue #40). A use case calls the
 * notifier after its commit on the request thread; whatever is slow (reading the recipient, talking to the mail server) happens
 * here, so a slow or down mail server never slows a booking.
 *
 * <ul>
 *   <li><b>Bounded:</b> at most {@code maxPending} tasks exist at once (running, queued or waiting for a retry). Beyond that the
 *       task is dropped and logged, never run on the caller.</li>
 *   <li><b>Retried with backoff:</b> a task that throws is run again after each delay of {@code retryDelays} (so
 *       {@code retryDelays.size() + 1} attempts in all). A waiting retry holds no thread: it is a delayed task.</li>
 *   <li><b>Not retried when pointless:</b> a task that throws {@link PermanentMailFailure} (a bad address, a template that cannot be
 *       filled, a 5xx refusal) runs once.</li>
 *   <li><b>Quiet logs:</b> a failure is logged with the task's description (ids only, written by the caller) and the exception
 *       <em>class</em>, never its message or stack trace, which can quote an address or a link.</li>
 * </ul>
 * In memory only: a restart loses what was waiting, as the rate limiter's state is lost (architecture.md section 11). A user whose
 * link never arrived asks again.
 */
public final class MailDispatcher {

    /** A unit of mail work; it may throw anything, and is retried when it does. */
    @FunctionalInterface
    public interface MailTask {
        void run() throws Exception;
    }

    private static final Logger LOG = LoggerFactory.getLogger(MailDispatcher.class);

    private final ScheduledThreadPoolExecutor executor;
    private final int maxPending;
    private final List<Duration> retryDelays;
    private final AtomicInteger pending = new AtomicInteger();

    public MailDispatcher(int threads, int maxPending, List<Duration> retryDelays) {
        if (threads < 1 || maxPending < 1) {
            throw new IllegalArgumentException("threads and maxPending must be positive");
        }
        this.maxPending = maxPending;
        this.retryDelays = List.copyOf(retryDelays);
        AtomicInteger numbering = new AtomicInteger();
        this.executor = new ScheduledThreadPoolExecutor(threads, runnable -> {
            Thread thread = new Thread(runnable, "mail-sender-" + numbering.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setRemoveOnCancelPolicy(true);
    }

    /** The production policy: two senders, {@code maxPending} tasks at most, three retries after 5 seconds, 30 seconds and 2 minutes. */
    public static MailDispatcher standard(int maxPending) {
        return new MailDispatcher(2, maxPending, List.of(Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(2)));
    }

    /**
     * Queues the task. {@code description} says what it is, by ids only (it is what the logs show); it must hold no address, name or
     * token. Returns false, after logging, when the bound is reached or the dispatcher is shut down.
     */
    public boolean submit(String description, MailTask task) {
        if (pending.incrementAndGet() > maxPending) {
            pending.decrementAndGet();
            LOG.warn("Mail dropped, the queue is full: {}", description);
            return false;
        }
        try {
            executor.execute(() -> attempt(description, task, 1));
            return true;
        } catch (RejectedExecutionException e) {
            pending.decrementAndGet();
            LOG.warn("Mail dropped, the sender is shut down: {}", description);
            return false;
        }
    }

    private void attempt(String description, MailTask task, int number) {
        boolean retrying = false;
        try {
            task.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Mail interrupted: {}", description);
        } catch (PermanentMailFailure failure) {
            LOG.error("Mail not delivered, permanent failure ({}), not retried: {}", failure.reason(), description);
        } catch (Exception failure) {
            retrying = retryOrGiveUp(description, task, number, failure);
        } catch (Error error) {
            LOG.error("Mail task died with {}: {}", error.getClass().getSimpleName(), description);
            throw error;
        } finally {
            if (!retrying) {
                pending.decrementAndGet();
            }
        }
    }

    /** Schedules the next attempt and returns true, or gives up (after logging) and returns false. */
    private boolean retryOrGiveUp(String description, MailTask task, int number, Exception failure) {
        int attempts = retryDelays.size() + 1;
        if (number >= attempts) {
            LOG.error("Mail not delivered after {} attempts ({}): {}", attempts, failure.getClass().getSimpleName(), description);
            return false;
        }
        Duration delay = retryDelays.get(number - 1);
        LOG.warn("Mail attempt {} of {} failed ({}), retrying in {} ms: {}", number, attempts, failure.getClass().getSimpleName(),
                delay.toMillis(), description);
        try {
            executor.schedule(() -> attempt(description, task, number + 1), delay.toNanos(), TimeUnit.NANOSECONDS);
            return true;
        } catch (RejectedExecutionException shutDown) {
            LOG.warn("Mail retry abandoned, the sender is shut down: {}", description);
            return false;
        }
    }

    /** Test-only: waits until nothing is queued, running or waiting for a retry. */
    boolean awaitIdle(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (pending.get() > 0) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            Thread.sleep(5);
        }
        return true;
    }

    /** Stops accepting work, lets the running attempts finish (a few seconds) and drops the retries still waiting. */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        int dropped = pending.get();
        if (dropped > 0) {
            LOG.warn("Mail sender stopped with {} message(s) not delivered", dropped);
        }
    }
}
