package net.metalmc.metal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Async entity processing system for safe operations like pathfinding.
 * Only performs calculations async - all state changes happen on main thread.
 */
public class AsyncEntityProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncEntityProcessor.class);

    private final ExecutorService entityWorkerPool;
    private final ThreadPriorityManager priorityManager;
    private final AtomicInteger activePathfinds = new AtomicInteger(0);
    private final AtomicInteger totalPathfinds = new AtomicInteger(0);

    // Timeout for async operations (milliseconds)
    private static final long PATHFIND_TIMEOUT_MS = 50;

    public AsyncEntityProcessor(ThreadPriorityManager priorityManager) {
        this.priorityManager = priorityManager;

        // Create thread pool for entity processing
        int threadCount = MetalConfig.entityProcessingThreads;
        this.entityWorkerPool = Executors.newFixedThreadPool(threadCount, new EntityWorkerThreadFactory());

        LOGGER.info("AsyncEntityProcessor initialized with {} threads", threadCount);
    }

    /**
     * Calculate pathfinding asynchronously
     * Returns a CompletableFuture that completes with the path or null if
     * timeout/error
     */
    public CompletableFuture<Path> calculatePathAsync(Mob mob, BlockPos target) {
        if (!MetalConfig.asyncPathfinding || !MetalConfig.asyncEntityProcessingEnabled) {
            // Fall back to sync pathfinding
            return CompletableFuture.completedFuture(null);
        }

        activePathfinds.incrementAndGet();
        totalPathfinds.incrementAndGet();

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
                .exceptionally(throwable -> {
                    // Timeout or error - return null to fall back to sync
                    return null;
                });
    }

    /**
     * Perform the actual pathfinding calculation
     * This is thread-safe as it only reads world state
     */
    private Path calculatePath(Mob mob, BlockPos target) {
        PathNavigation navigation = mob.getNavigation();

        // Note: This is a simplified version
        // The actual implementation would need to ensure thread-safe access to world
        // data
        // For now, this returns null to indicate sync fallback

        // TODO: Implement thread-safe pathfinding calculation
        // This would require:
        // 1. Snapshot of relevant world blocks
        // 2. Pathfinding algorithm on snapshot
        // 3. Return path for main thread to apply

        return null;
    }

    /**
     * Calculate collision detection asynchronously
     * Only enabled if configured (experimental)
     */
    public CompletableFuture<Boolean> checkCollisionAsync(Mob mob, BlockPos pos) {
        if (!MetalConfig.asyncCollisionDetection || !MetalConfig.asyncEntityProcessingEnabled) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Thread-safe collision check
                // Would need to snapshot relevant collision boxes
                return false; // Placeholder
            } catch (Exception e) {
                LOGGER.warn("Error in async collision detection: {}", e.getMessage());
                return false;
            }
        }, entityWorkerPool)
                .orTimeout(10, TimeUnit.MILLISECONDS)
                .exceptionally(throwable -> false);
    }

    /**
     * Get processing statistics
     */
    public ProcessingStatistics getStatistics() {
        return new ProcessingStatistics(
                totalPathfinds.get(),
                activePathfinds.get());
    }

    /**
     * Shutdown the entity processor
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

    /**
     * Thread factory for entity worker threads
     */
    private class EntityWorkerThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "MetalMC-EntityWorker-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);

            // Set thread priority
            priorityManager.setWorkerThreadPriority(thread, ThreadPriorityManager.WorkerType.ENTITY_PROCESSING);

            return thread;
        }
    }

    /**
     * Processing statistics record
     */
    public record ProcessingStatistics(
            int totalPathfinds,
            int activePathfinds) {
        @Override
        public String toString() {
            return String.format(
                    "EntityProcessing Stats: TotalPathfinds=%d, Active=%d",
                    totalPathfinds, activePathfinds);
        }
    }
}
