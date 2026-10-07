package com.regivolley.api.infrastructure.persistence.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs competing writers in separate threads, hence separate committed transactions. */
final class Races {

    private Races() {
    }

    /**
     * Every contender first loads its own copy (all see the same version), waits at a start gate and
     * then runs its save; the saves are released together. Returns the failure of each contender
     * that failed (none for the one that succeeded).
     */
    static <T> List<Throwable> race(Callable<T> load, List<Function<T, ?>> saves) throws Exception {
        return race(saves.stream().map(save -> load).toList(), saves);
    }

    /** As above, but every contender loads its own thing (e.g. a different session). */
    static <T> List<Throwable> race(List<Callable<T>> loads, List<Function<T, ?>> saves) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(saves.size());
        try {
            CountDownLatch loaded = new CountDownLatch(saves.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < saves.size(); i++) {
                Callable<T> load = loads.get(i);
                Function<T, ?> save = saves.get(i);
                futures.add(pool.submit(() -> {
                    T copy = load.call();
                    loaded.countDown();
                    go.await();
                    return save.apply(copy);
                }));
            }
            assertThat(loaded.await(30, TimeUnit.SECONDS)).as("every contender loaded its copy").isTrue();
            go.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<?> future : futures) {
                try {
                    future.get(60, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return failures;
        } finally {
            pool.shutdownNow();
        }
    }
}
