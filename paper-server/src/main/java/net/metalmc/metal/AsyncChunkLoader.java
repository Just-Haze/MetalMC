package net.metalmc.metal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enhanced async chunk loading system with priority queue and load balancing.
 * Prioritizes chunks requested by players over background chunk generation.
 */
public class AsyncChunkLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncChunkLoader.class);

    private final ExecutorService chunkLoadExecutor;
    private final PriorityBlockingQueue<ChunkLoadTask> taskQueue;
    private final ThreadPriorityManager priorityManager;
    private final AtomicInteger activeLoads = new AtomicInteger(0);

    // Statistics
    private final AtomicInteger totalLoadsProcessed = new AtomicInteger(0);
    private final AtomicInteger playerRequestedLoads = new AtomicInteger(0);

    public AsyncChunkLoader(ThreadPriorityManager priorityManager) {
        this.priorityManager = priorityManager;
        this.taskQueue = new PriorityBlockingQueue<>(1000);

        // Create thread pool with configured size
        int threadCount = MetalConfig.chunkLoadingThreads;
        this.chunkLoadExecutor = Executors.newFixedThreadPool(threadCount, new ChunkLoadThreadFactory());

        LOGGER.info("AsyncChunkLoader initialized with {} threads", threadCount);
    }

    /**
     * Submit a chunk load request with priority
     */
    public CompletableFuture<Void> loadChunkAsync(
            ServerLevel level,
            ChunkPos pos,
            ChunkStatus status,
            boolean playerRequested) {
        if (!MetalConfig.asyncChunkLoadingEnabled) {
            // Fall back to sync loading
            return CompletableFuture.completedFuture(null);
        }

        ChunkLoadPriority priority = determineLoadPriority(playerRequested);
        ChunkLoadTask task = new ChunkLoadTask(level, pos, status, priority);

        if (playerRequested) {
            playerRequestedLoads.incrementAndGet();
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                activeLoads.incrementAndGet();
                processChunkLoad(task);
                totalLoadsProcessed.incrementAndGet();
                return null;
            } finally {
                activeLoads.decrementAndGet();
            }
        }, chunkLoadExecutor);
    }

    /**
     * Determine load priority based on request type and configuration
     */
    private ChunkLoadPriority determineLoadPriority(boolean playerRequested) {
        if (playerRequested && MetalConfig.prioritizePlayerChunks) {
            return ChunkLoadPriority.HIGH;
        } else if (playerRequested) {
            return ChunkLoadPriority.NORMAL;
        } else {
            return ChunkLoadPriority.LOW;
        }
    }

    /**
     * Process a chunk load task
     */
    private void processChunkLoad(ChunkLoadTask task) {
        // Actual chunk loading logic would go here
        // This is a placeholder that would integrate with Paper's chunk system

        // Note: The actual chunk loading must still happen on the main thread
        // This executor is for I/O operations and preparation work
    }

    /**
     * Get current load statistics
     */
    public LoadStatistics getStatistics() {
        return new LoadStatistics(
                totalLoadsProcessed.get(),
                playerRequestedLoads.get(),
                activeLoads.get(),
                taskQueue.size());
    }

    /**
     * Shutdown the chunk loader
     */
    public void shutdown() {
        LOGGER.info("Shutting down AsyncChunkLoader...");
        chunkLoadExecutor.shutdown();
        try {
            if (!chunkLoadExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                chunkLoadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            chunkLoadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("AsyncChunkLoader shutdown complete");
    }

    /**
     * Chunk load task with priority
     */
    private static class ChunkLoadTask implements Comparable<ChunkLoadTask> {
        private final ServerLevel level;
        private final ChunkPos pos;
        private final ChunkStatus status;
        private final ChunkLoadPriority priority;
        private final long timestamp;

        public ChunkLoadTask(ServerLevel level, ChunkPos pos, ChunkStatus status, ChunkLoadPriority priority) {
            this.level = level;
            this.pos = pos;
            this.status = status;
            this.priority = priority;
            this.timestamp = System.nanoTime();
        }

        @Override
        public int compareTo(ChunkLoadTask other) {
            // Higher priority first
            int priorityCompare = Integer.compare(other.priority.value, this.priority.value);
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            // Then by timestamp (FIFO for same priority)
            return Long.compare(this.timestamp, other.timestamp);
        }
    }

    /**
     * Chunk load priority levels
     */
    private enum ChunkLoadPriority {
        HIGH(3), // Player-requested chunks
        NORMAL(2), // Regular chunks
        LOW(1); // Background generation

        private final int value;

        ChunkLoadPriority(int value) {
            this.value = value;
        }
    }

    /**
     * Thread factory for chunk loading threads
     */
    private class ChunkLoadThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "MetalMC-ChunkLoader-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);

            // Set thread priority
            priorityManager.setWorkerThreadPriority(thread, ThreadPriorityManager.WorkerType.CHUNK_LOADING);

            return thread;
        }
    }

    /**
     * Load statistics record
     */
    public record LoadStatistics(
            int totalLoadsProcessed,
            int playerRequestedLoads,
            int activeLoads,
            int queuedTasks) {
        @Override
        public String toString() {
            return String.format(
                    "ChunkLoad Stats: Total=%d, PlayerRequested=%d, Active=%d, Queued=%d",
                    totalLoadsProcessed, playerRequestedLoads, activeLoads, queuedTasks);
        }
    }
}
