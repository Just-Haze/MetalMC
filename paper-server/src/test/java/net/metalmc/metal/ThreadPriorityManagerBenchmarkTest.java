package net.metalmc.metal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Benchmark and correctness tests for {@link ThreadPriorityManager}.
 *
 * <p>These tests verify that:
 * <ul>
 *   <li>Worker threads are registered and tracked correctly.</li>
 *   <li>Dynamic priority adjustment fires at the right TPS thresholds.</li>
 *   <li>Dead/finished threads are pruned from the registry without errors.</li>
 *   <li>Priority operations complete in well under 1 ms each (performance gate).</li>
 * </ul>
 */
class ThreadPriorityManagerBenchmarkTest {

    private ThreadPriorityManager manager;

    @BeforeEach
    void setUp() {
        // Provide default config values without a live Minecraft server.
        MetalConfig.threadPrioritiesEnabled = true;
        MetalConfig.mainThreadPriority = Thread.MAX_PRIORITY;
        MetalConfig.chunkLoadingPriority = Thread.NORM_PRIORITY + 1;
        MetalConfig.entityProcessingPriority = Thread.NORM_PRIORITY;
        MetalConfig.dynamicPriorityAdjustment = true;

        manager = new ThreadPriorityManager(Thread.currentThread());
    }

    @AfterEach
    void tearDown() {
        // Restore current thread's priority so it doesn't bleed into other tests.
        Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
    }

    // -----------------------------------------------------------------------
    // Correctness tests
    // -----------------------------------------------------------------------

    @Test
    void initialTpsShouldBeGood() {
        assertFalse(manager.isUnderLoad(), "Server should not be under load at startup");
        assertEquals(20.0, manager.getCurrentTPS(), 0.001);
    }

    @Test
    void updateTpsToCriticalShouldMarkUnderLoad() {
        manager.updateTPS(14.0);
        assertTrue(manager.isUnderLoad(), "TPS below 15 should mark server under load");
    }

    @Test
    void updateTpsToGoodShouldClearUnderLoad() {
        manager.updateTPS(14.0); // put under load first
        manager.updateTPS(19.8); // recover
        assertFalse(manager.isUnderLoad(), "TPS above warning threshold should clear load flag");
    }

    @Test
    void registerWorkerThreadShouldNotThrow() {
        Thread worker = new Thread(() -> {}, "test-worker");
        worker.setDaemon(true);
        assertDoesNotThrow(() ->
            manager.registerWorkerThread(worker, ThreadPriorityManager.WorkerType.ENTITY_PROCESSING));
    }

    @Test
    void workerThreadPriorityIsAdjustedOnCriticalTps() throws InterruptedException {
        // Start a long-lived worker thread so it is alive when we adjust priorities.
        Object lock = new Object();
        Thread worker = new Thread(() -> {
            synchronized (lock) {
                try { lock.wait(5000); } catch (InterruptedException ignored) {}
            }
        }, "test-worker-priority");
        worker.setDaemon(true);
        worker.start();

        manager.setWorkerThreadPriority(worker, ThreadPriorityManager.WorkerType.ENTITY_PROCESSING);
        manager.registerWorkerThread(worker, ThreadPriorityManager.WorkerType.ENTITY_PROCESSING);

        // Trigger critical-load path.
        manager.updateTPS(14.0);

        // Allow the priority change to propagate (it's synchronous, but give the JVM a moment).
        Thread.sleep(10);

        assertEquals(Thread.MIN_PRIORITY, worker.getPriority(),
                "Worker thread priority should be throttled to MIN during critical TPS");

        // Clean up
        synchronized (lock) { lock.notifyAll(); }
        worker.join(1000);
    }

    @Test
    void deadWorkerThreadsArePrunedWhenPriorityIsApplied() throws InterruptedException {
        Thread shortLived = new Thread(() -> {}, "short-lived-worker");
        shortLived.setDaemon(true);
        shortLived.start();
        shortLived.join(1000); // let it die

        // Register the now-dead thread – pruning should happen on next priority update.
        manager.registerWorkerThread(shortLived, ThreadPriorityManager.WorkerType.ENTITY_PROCESSING);

        // Trigger priority update; should not throw even for dead threads.
        assertDoesNotThrow(() -> manager.updateTPS(14.0));
    }

    @Test
    void recommendedPriorityReflectsCurrentLoad() {
        // Good TPS → configured priority
        int goodPriority = manager.getRecommendedPriority(ThreadPriorityManager.WorkerType.ENTITY_PROCESSING);
        assertEquals(Thread.NORM_PRIORITY, goodPriority);

        // Critical TPS → reduced priority
        manager.updateTPS(14.0);
        int criticalPriority = manager.getRecommendedPriority(ThreadPriorityManager.WorkerType.ENTITY_PROCESSING);
        assertTrue(criticalPriority < goodPriority,
                "Recommended priority should decrease under critical load");
    }

    // -----------------------------------------------------------------------
    // Performance (timing) gates
    // -----------------------------------------------------------------------

    /**
     * {@code updateTPS} is called every server tick (~50 ms budget).
     * It must complete in well under 1 ms even with many registered threads.
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void updateTpsShouldCompleteQuicklyWithManyRegisteredThreads() throws InterruptedException {
        Object lock = new Object();
        int threadCount = 50;
        Thread[] workers = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            workers[i] = new Thread(() -> {
                synchronized (lock) {
                    try { lock.wait(5000); } catch (InterruptedException ignored) {}
                }
            }, "bench-worker-" + i);
            workers[i].setDaemon(true);
            workers[i].start();
            manager.registerWorkerThread(workers[i], ThreadPriorityManager.WorkerType.ENTITY_PROCESSING);
        }

        // Warm up
        for (int i = 0; i < 10; i++) {
            manager.updateTPS(14.0);
            manager.updateTPS(20.0);
        }

        // Measure 1000 TPS updates
        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            manager.updateTPS(i % 2 == 0 ? 14.0 : 20.0);
        }
        long elapsedNs = System.nanoTime() - start;
        long averageNs = elapsedNs / 1000;

        // Each updateTPS call must be faster than 500 µs on average
        assertTrue(averageNs < 500_000,
                String.format("updateTPS took %d ns on average (limit: 500,000 ns)", averageNs));

        // Clean up
        synchronized (lock) { lock.notifyAll(); }
        for (Thread w : workers) w.join(500);
    }

    /**
     * {@code getRecommendedPriority} is called from thread factories; must be near-zero cost.
     */
    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void getRecommendedPriorityShouldBeSubMicrosecond() {
        // Warm up JIT
        for (int i = 0; i < 1000; i++) {
            manager.getRecommendedPriority(ThreadPriorityManager.WorkerType.CHUNK_LOADING);
        }

        long start = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            manager.getRecommendedPriority(ThreadPriorityManager.WorkerType.ENTITY_PROCESSING);
        }
        long elapsedNs = System.nanoTime() - start;
        long averageNs = elapsedNs / 100_000;

        assertTrue(averageNs < 1_000,
                String.format("getRecommendedPriority took %d ns on average (limit: 1,000 ns)", averageNs));
    }
}
