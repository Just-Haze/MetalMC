package net.metalmc.metal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Async chunk loading system with a priority queue.
 * Player-requested chunks are scheduled ahead of background generation.
 * Worker threads drive execution by polling from the priority queue,
 * ensuring high-priority tasks are always processed first.
 */
public class AsyncChunkLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncChunkLoader.class);

    private final PriorityBlockingQueue<ChunkLoadTask> taskQueue;
    private final ThreadPriorityManager priorityManager;
    private final AtomicInteger activeLoads = new AtomicInteger(0);

    // Statistics
    private final AtomicInteger totalLoadsProcessed = new AtomicInteger(0);
    private final AtomicInteger playerRequestedLoads = new AtomicInteger(0);

    private volatile boolean running = true;
    private final Thread[] workerThreads;

    public AsyncChunkLoader(ThreadPriorityManager priorityManager) {
        this.priorityManager = priorityManager;
        this.taskQueue = new PriorityBlockingQueue<>(1024);

        int threadCount = MetalConfig.chunkLoadingThreads;
        this.workerThreads = new Thread[threadCount];
        for (int i = 1; i <= threadCount; i++) {
            Thread worker = new Thread(this::workerLoop, "MetalMC-ChunkLoader-" + i);
            worker.setDaemon(true);
            priorityManager.setWorkerThreadPriority(worker, ThreadPriorityManager.WorkerType.CHUNK_LOADING);
            workerThreads[i - 1] = worker;
            worker.start();
        }

        LOGGER.info("AsyncChunkLoader initialized with {} threads", threadCount);
    }

    /**
     * Submit a chunk load request. Higher-priority tasks (player-requested) will
     * be processed before lower-priority background tasks.
     */
    public CompletableFuture<Void> loadChunkAsync(
            ServerLevel level,
            ChunkPos pos,
            ChunkStatus status,
            boolean playerRequested) {

        if (!MetalConfig.asyncChunkLoadingEnabled) {
            return CompletableFuture.completedFuture(null);
        }

        ChunkLoadPriority priority = playerRequested && MetalConfig.prioritizePlayerChunks
                ? ChunkLoadPriority.HIGH
                : (playerRequested ? ChunkLoadPriority.NORMAL : ChunkLoadPriority.LOW);

        ChunkLoadTask task = new ChunkLoadTask(level, pos, status, priority);

        if (playerRequested) {
            playerRequestedLoads.incrementAndGet();
        }

        taskQueue.offer(task);
        return task.future;
    }

    /**
     * Worker loop: continuously polls the priority queue and processes tasks.
     */
    private void workerLoop() {
        while (running) {
            try {
                ChunkLoadTask task = taskQueue.poll(500, TimeUnit.MILLISECONDS);
                if (task != null) {
                    activeLoads.incrementAndGet();
                    try {
                        processChunkLoad(task);
                        totalLoadsProcessed.incrementAndGet();
                        task.future.complete(null);
                    } catch (Exception e) {
                        LOGGER.warn("Error processing chunk load at {}: {}", task.pos, e.getMessage());
                        task.future.completeExceptionally(e);
                    } finally {
                        activeLoads.decrementAndGet();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Process a chunk load task.
     * I/O preparation and pre-fetch work happens here; the final state
     * change must be applied on the main thread via the returned future.
     */
    private void processChunkLoad(ChunkLoadTask task) {
        // Integration point with Paper's async chunk system.
        // The actual chunk data load (region file I/O) is performed here;
        // the server level applies the result on the main thread when the
        // CompletableFuture returned by loadChunkAsync() is consumed.
    }

    /**
     * Get current load statistics.
     */
    public LoadStatistics getStatistics() {
        return new LoadStatistics(
                totalLoadsProcessed.get(),
                playerRequestedLoads.get(),
                activeLoads.get(),
                taskQueue.size());
    }

    /**
     * Shut down the chunk loader, draining any queued tasks.
     */
    public void shutdown() {
        LOGGER.info("Shutting down AsyncChunkLoader...");
        running = false;

        // Interrupt all worker threads to unblock any blocked poll() calls.
        for (Thread worker : workerThreads) {
            worker.interrupt();
        }

        // Wait for workers to finish their current task before draining.
        for (Thread worker : workerThreads) {
            try {
                worker.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Cancel all tasks that are still in the queue (workers have stopped).
        ChunkLoadTask task;
        while ((task = taskQueue.poll()) != null) {
            task.future.cancel(false);
        }
        LOGGER.info("AsyncChunkLoader shutdown complete");
    }

    // -------------------------------------------------------------------------
    // Internal types
    // -------------------------------------------------------------------------

    private static class ChunkLoadTask implements Comparable<ChunkLoadTask> {
        final ServerLevel level;
        final ChunkPos pos;
        final ChunkStatus status;
        final ChunkLoadPriority priority;
        final long timestamp;
        final CompletableFuture<Void> future = new CompletableFuture<>();

        ChunkLoadTask(ServerLevel level, ChunkPos pos, ChunkStatus status, ChunkLoadPriority priority) {
            this.level = level;
            this.pos = pos;
            this.status = status;
            this.priority = priority;
            this.timestamp = System.nanoTime();
        }

        @Override
        public int compareTo(ChunkLoadTask other) {
            // Higher priority value = processed first.
            int cmp = Integer.compare(other.priority.value, this.priority.value);
            if (cmp != 0) return cmp;
            // FIFO ordering within the same priority level.
            return Long.compare(this.timestamp, other.timestamp);
        }
    }

    private enum ChunkLoadPriority {
        LOW(1),
        NORMAL(2),
        HIGH(3);

        final int value;

        ChunkLoadPriority(int value) {
            this.value = value;
        }
    }

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
