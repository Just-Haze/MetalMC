package net.metalmc.metal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages thread priorities dynamically based on server performance.
 * Ensures the main thread gets maximum priority while worker threads
 * are adjusted based on TPS and load.
 */
public class ThreadPriorityManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadPriorityManager.class);

    private final Thread mainThread;
    private volatile double currentTPS = 20.0;
    private volatile boolean underLoad = false;

    /** Weak references to all registered worker threads so they can be adjusted dynamically. */
    private final CopyOnWriteArrayList<WorkerThreadEntry> workerThreads = new CopyOnWriteArrayList<>();

    /** Pairs a weak thread reference with its WorkerType so default priorities can be restored. */
    private record WorkerThreadEntry(WeakReference<Thread> ref, WorkerType type) {}

    // TPS thresholds
    private static final double TPS_CRITICAL = 15.0;
    private static final double TPS_WARNING = 18.0;
    private static final double TPS_GOOD = 19.5;

    public ThreadPriorityManager(Thread mainThread) {
        this.mainThread = mainThread;
        initializeMainThreadPriority();
    }

    /**
     * Initialize main thread priority to maximum
     */
    private void initializeMainThreadPriority() {
        if (MetalConfig.threadPrioritiesEnabled) {
            try {
                mainThread.setPriority(MetalConfig.mainThreadPriority);
                LOGGER.info("Set main thread priority to {}", MetalConfig.mainThreadPriority);
            } catch (SecurityException e) {
                LOGGER.warn("Failed to set main thread priority: {}", e.getMessage());
            }
        }
    }

    /**
     * Register a worker thread so its priority can be adjusted dynamically.
     * Uses a WeakReference so finished threads are automatically garbage-collected.
     */
    public void registerWorkerThread(Thread thread, WorkerType type) {
        workerThreads.add(new WorkerThreadEntry(new WeakReference<>(thread), type));
    }

    /**
     * Set priority for a worker thread
     */
    public void setWorkerThreadPriority(Thread thread, WorkerType type) {
        if (!MetalConfig.threadPrioritiesEnabled) {
            return;
        }

        int priority = switch (type) {
            case CHUNK_LOADING -> MetalConfig.chunkLoadingPriority;
            case ENTITY_PROCESSING -> MetalConfig.entityProcessingPriority;
            case TILE_ENTITY -> Math.max(Thread.MIN_PRIORITY, MetalConfig.entityProcessingPriority - 1);
            case BACKGROUND -> Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 2);
        };

        try {
            thread.setPriority(Math.min(Thread.MAX_PRIORITY, Math.max(Thread.MIN_PRIORITY, priority)));
        } catch (SecurityException e) {
            LOGGER.warn("Failed to set {} thread priority: {}", type, e.getMessage());
        }
    }

    /**
     * Update TPS and adjust priorities if dynamic adjustment is enabled
     */
    public void updateTPS(double tps) {
        this.currentTPS = tps;

        if (MetalConfig.dynamicPriorityAdjustment) {
            boolean wasUnderLoad = underLoad;
            underLoad = tps < TPS_WARNING;

            if (underLoad != wasUnderLoad) {
                adjustPrioritiesForLoad();
            }
        }
    }

    /**
     * Dynamically adjust worker thread priorities based on server load
     */
    private void adjustPrioritiesForLoad() {
        if (currentTPS < TPS_CRITICAL) {
            // Critical: Boost main thread, throttle workers
            LOGGER.info("TPS critical ({} < {}), boosting main thread priority", currentTPS, TPS_CRITICAL);
            boostMainThread();
            throttleWorkers();
        } else if (currentTPS < TPS_WARNING) {
            // Warning: Slightly reduce worker priorities
            LOGGER.debug("TPS warning ({} < {}), reducing worker thread priorities", currentTPS, TPS_WARNING);
            reduceWorkerPriorities();
        } else {
            // Good: Restore normal priorities
            restoreNormalPriorities();
        }
    }

    /**
     * Boost main thread priority to maximum
     */
    private void boostMainThread() {
        try {
            mainThread.setPriority(Thread.MAX_PRIORITY);
        } catch (SecurityException e) {
            LOGGER.warn("Failed to boost main thread priority: {}", e.getMessage());
        }
    }

    /**
     * Throttle worker threads to minimum priority during critical load.
     */
    private void throttleWorkers() {
        applyWorkerPriority(Thread.MIN_PRIORITY);
    }

    /**
     * Reduce worker thread priorities slightly during warning-level load.
     */
    private void reduceWorkerPriorities() {
        applyWorkerPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
    }

    /**
     * Restore normal thread priorities when server is running well.
     */
    private void restoreNormalPriorities() {
        try {
            mainThread.setPriority(MetalConfig.mainThreadPriority);
        } catch (SecurityException e) {
            LOGGER.warn("Failed to restore main thread priority: {}", e.getMessage());
        }
        applyWorkerPriority(-1); // -1 signals "use configured defaults"
    }

    /**
     * Apply a priority to all live registered worker threads.
     * A priority of {@code -1} means "restore each thread to its configured default"
     * (using the {@link WorkerType} recorded at registration time).
     */
    private void applyWorkerPriority(int priority) {
        // Prune dead entries in a single pass (O(1) array copies instead of O(n)).
        workerThreads.removeIf(entry -> {
            Thread t = entry.ref().get();
            return t == null || !t.isAlive();
        });

        for (WorkerThreadEntry entry : workerThreads) {
            Thread t = entry.ref().get();
            if (t == null || !t.isAlive()) {
                continue;
            }
            int target = priority < 0 ? configuredPriority(entry.type()) : priority;
            try {
                t.setPriority(Math.min(Thread.MAX_PRIORITY, Math.max(Thread.MIN_PRIORITY, target)));
            } catch (SecurityException e) {
                LOGGER.warn("Failed to adjust worker thread priority: {}", e.getMessage());
            }
        }
    }

    /**
     * Look up the configured base priority for the given {@link WorkerType}.
     */
    private int configuredPriority(WorkerType type) {
        return switch (type) {
            case CHUNK_LOADING -> MetalConfig.chunkLoadingPriority;
            case ENTITY_PROCESSING -> MetalConfig.entityProcessingPriority;
            case TILE_ENTITY -> Math.max(Thread.MIN_PRIORITY, MetalConfig.entityProcessingPriority - 1);
            case BACKGROUND -> Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 2);
        };
    }

    /**
     * Get current TPS
     */
    public double getCurrentTPS() {
        return currentTPS;
    }

    /**
     * Check if server is under load
     */
    public boolean isUnderLoad() {
        return underLoad;
    }

    /**
     * Get recommended priority for worker type based on current load
     */
    public int getRecommendedPriority(WorkerType type) {
        if (!MetalConfig.threadPrioritiesEnabled) {
            return Thread.NORM_PRIORITY;
        }

        int basePriority = switch (type) {
            case CHUNK_LOADING -> MetalConfig.chunkLoadingPriority;
            case ENTITY_PROCESSING -> MetalConfig.entityProcessingPriority;
            case TILE_ENTITY -> Math.max(Thread.MIN_PRIORITY, MetalConfig.entityProcessingPriority - 1);
            case BACKGROUND -> Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 2);
        };

        // Reduce priority if under load
        if (underLoad && MetalConfig.dynamicPriorityAdjustment) {
            if (currentTPS < TPS_CRITICAL) {
                basePriority = Math.max(Thread.MIN_PRIORITY, basePriority - 2);
            } else if (currentTPS < TPS_WARNING) {
                basePriority = Math.max(Thread.MIN_PRIORITY, basePriority - 1);
            }
        }

        return Math.min(Thread.MAX_PRIORITY, Math.max(Thread.MIN_PRIORITY, basePriority));
    }

    /**
     * Worker thread types
     */
    public enum WorkerType {
        CHUNK_LOADING,
        ENTITY_PROCESSING,
        TILE_ENTITY,
        BACKGROUND
    }
}
