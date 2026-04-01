package net.metalmc.metal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Async entity processing for safe operations like pathfinding.
 * Only performs read-only calculations on worker threads; all state
 * changes must be applied back on the main server thread.
 */
public class AsyncEntityProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncEntityProcessor.class);

    private final ExecutorService entityWorkerPool;
    private final ThreadPriorityManager priorityManager;
    private final AtomicInteger activePathfinds = new AtomicInteger(0);
    private final AtomicInteger totalPathfinds = new AtomicInteger(0);

    private static final long PATHFIND_TIMEOUT_MS = 50;

    public AsyncEntityProcessor(ThreadPriorityManager priorityManager) {
        this.priorityManager = priorityManager;

        int threadCount = MetalConfig.entityProcessingThreads;
        AtomicInteger threadNumber = new AtomicInteger(1);
        this.entityWorkerPool = Executors.newFixedThreadPool(threadCount, r -> {
            Thread thread = new Thread(r, "MetalMC-EntityWorker-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            priorityManager.setWorkerThreadPriority(thread, ThreadPriorityManager.WorkerType.ENTITY_PROCESSING);
            return thread;
        });

        LOGGER.info("AsyncEntityProcessor initialized with {} threads", threadCount);
    }

    /**
     * Calculate pathfinding asynchronously.
     * Returns a future that resolves to the calculated {@link Path}, or {@code null}
     * if the operation times out, is disabled, or encounters an error — in which
     * case the caller should fall back to synchronous pathfinding.
     */
    public CompletableFuture<Path> calculatePathAsync(Mob mob, BlockPos target) {
        if (!MetalConfig.asyncPathfinding || !MetalConfig.asyncEntityProcessingEnabled) {
            return CompletableFuture.completedFuture(null);
        }

        totalPathfinds.incrementAndGet();
        activePathfinds.incrementAndGet();

        return CompletableFuture.supplyAsync(() -> {
            try {
                return calculatePath(mob, target);
            } catch (Exception e) {
                LOGGER.warn("Error in async pathfinding for {}: {}", mob.getType(), e.getMessage());
                return null;
            } finally {
                activePathfinds.decrementAndGet();
            }
        }, entityWorkerPool)
                .orTimeout(PATHFIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .exceptionally(throwable -> null);
    }

    /**
     * Perform the actual pathfinding calculation on a worker thread.
     * This method must only read immutable or thread-safe world state;
     * it must not modify any game state.
     *
     * <p>A full implementation would snapshot the relevant block/collision
     * data for the area between the mob and target, run the pathfinder on
     * that snapshot, and return the resulting {@link Path} for the main
     * thread to apply via {@code mob.getNavigation().moveTo(path, speed)}.
     */
    private Path calculatePath(Mob mob, BlockPos target) {
        // TODO: Implement thread-safe pathfinding using a world snapshot.
        // Steps required:
        //   1. Capture a read-only snapshot of blocks in the search region.
        //   2. Run PathFinder#findBlockPath() on the snapshot.
        //   3. Return the path; main thread applies it via PathNavigation.
        return null;
    }

    /**
     * Perform an asynchronous collision check.
     * Only enabled when {@code async-collision-detection} is {@code true} (experimental).
     */
    public CompletableFuture<Boolean> checkCollisionAsync(Mob mob, BlockPos pos) {
        if (!MetalConfig.asyncCollisionDetection || !MetalConfig.asyncEntityProcessingEnabled) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // TODO: Snapshot relevant collision boxes and perform check.
                return false;
            } catch (Exception e) {
                LOGGER.warn("Error in async collision detection: {}", e.getMessage());
                return false;
            }
        }, entityWorkerPool)
                .orTimeout(10, TimeUnit.MILLISECONDS)
                .exceptionally(throwable -> false);
    }

    /**
     * Get current processing statistics.
     */
    public ProcessingStatistics getStatistics() {
        return new ProcessingStatistics(totalPathfinds.get(), activePathfinds.get());
    }

    /**
     * Shut down the entity processor, waiting for in-flight tasks to finish.
     */
    public void shutdown() {
        LOGGER.info("Shutting down AsyncEntityProcessor...");
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

    public record ProcessingStatistics(int totalPathfinds, int activePathfinds) {
        @Override
        public String toString() {
            return String.format(
                    "EntityProcessing Stats: TotalPathfinds=%d, Active=%d",
                    totalPathfinds, activePathfinds);
        }
    }
}
