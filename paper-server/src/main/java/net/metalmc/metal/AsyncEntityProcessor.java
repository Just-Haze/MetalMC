package net.metalmc.metal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Async entity processing helpers for MetalMC.
 *
 * <p>This class manages a small thread pool for offloading CPU-bound,
 * read-only entity computations.  All <em>state mutations</em> must still
 * be applied on the main server thread; workers only perform calculations
 * and return results via {@link CompletableFuture}.
 *
 * <p>Currently provides:
 * <ul>
 *   <li>A pre-warmed thread pool for future async work submission.</li>
 *   <li>{@link #shouldProcessEntity} — a fast proximity gate that
 *       combines EAR and DAB checks before expensive work begins.</li>
 *   <li>Basic statistics accessible via {@link #getStatistics()}.</li>
 * </ul>
 */
public class AsyncEntityProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncEntityProcessor.class);

    private final ExecutorService entityWorkerPool;
    private final ThreadPriorityManager priorityManager;
    private final AtomicInteger activeTasks = new AtomicInteger(0);
    private final AtomicLong totalTasksSubmitted = new AtomicLong(0);

    public AsyncEntityProcessor(ThreadPriorityManager priorityManager) {
        this.priorityManager = priorityManager;

        int threadCount = MetalConfig.entityProcessingThreads;
        this.entityWorkerPool = Executors.newFixedThreadPool(threadCount, new EntityWorkerThreadFactory());

        LOGGER.info("AsyncEntityProcessor initialised with {} threads", threadCount);
    }

    /**
     * Submits a read-only callable to the entity worker pool.
     *
     * <p>The callable must not write to any shared Minecraft state.
     * The returned future completes with the callable's result, or
     * {@code null} on error or timeout.
     *
     * @param task      the computation to run off the main thread
     * @param timeoutMs maximum time to wait for the result (milliseconds)
     * @param <T>       result type
     * @return future that completes with the result, or {@code null}
     */
    public <T> CompletableFuture<T> submitReadOnly(final Callable<T> task, final long timeoutMs) {
        totalTasksSubmitted.incrementAndGet();
        activeTasks.incrementAndGet();

        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                LOGGER.warn("Async entity task failed: {}", e.getMessage());
                return null;
            } finally {
                activeTasks.decrementAndGet();
            }
        }, entityWorkerPool)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(throwable -> null);
    }

    /**
     * Returns {@code true} when {@code mob} should have expensive processing
     * performed on it this tick.
     *
     * <p>Combines:
     * <ol>
     *   <li>A quick distance gate via
     *       {@link EntityActivationOptimizer#isPlayerWithinRange} — skips
     *       entities that are completely outside player view.</li>
     *   <li>The DAB throttle via {@link DynamicActivationBrain#shouldTickBrain}
     *       — limits AI ticks for entities between the start distance and
     *       view distance.</li>
     * </ol>
     *
     * <p>Call this before any pathfinding, goal evaluation, or expensive
     * AI work on a mob.
     *
     * @param mob the mob to test
     * @return {@code true} if the mob should be processed this tick
     */
    public static boolean shouldProcessEntity(final Mob mob) {
        // Hard gate: more than 128 blocks from every player → skip entirely.
        if (!EntityActivationOptimizer.isPlayerWithinRange(mob, 128.0)) {
            return false;
        }
        // DAB throttle for entities in the mid-range zone.
        return DynamicActivationBrain.shouldTickBrain(mob);
    }

    /**
     * Returns current processing statistics.
     */
    public ProcessingStatistics getStatistics() {
        return new ProcessingStatistics(totalTasksSubmitted.get(), activeTasks.get());
    }

    /**
     * Shuts down the worker pool gracefully.
     */
    public void shutdown() {
        LOGGER.info("Shutting down AsyncEntityProcessor…");
        entityWorkerPool.shutdown();
        try {
            if (!entityWorkerPool.awaitTermination(10, TimeUnit.SECONDS)) {
                entityWorkerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            entityWorkerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("AsyncEntityProcessor shutdown complete");
    }

    private class EntityWorkerThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "MetalMC-EntityWorker-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            priorityManager.setWorkerThreadPriority(thread, ThreadPriorityManager.WorkerType.ENTITY_PROCESSING);
            return thread;
        }
    }

    public record ProcessingStatistics(long totalTasksSubmitted, int activeTasks) {
        @Override
        public String toString() {
            return String.format("EntityProcessing Stats: Total=%d, Active=%d",
                    totalTasksSubmitted, activeTasks);
        }
    }
}

