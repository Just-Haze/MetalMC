package net.metalmc.metal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /** Tracks all registered worker threads and their types for dynamic adjustment. */
    private final ConcurrentHashMap<Thread, WorkerType> workerThreads = new ConcurrentHashMap<>();

    // TPS thresholds
    private static final double TPS_CRITICAL = 15.0;
    private static final double TPS_WARNING = 18.0;

    public ThreadPriorityManager(Thread mainThread) {
        this.mainThread = mainThread;
        initializeMainThreadPriority();
    }

    /**
     * Initialize main thread priority to the configured value.
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
     * Register a worker thread so its priority can be dynamically adjusted.
     */
    public void registerWorkerThread(Thread thread, WorkerType type) {
        workerThreads.put(thread, type);
    }

    /**
     * Unregister a worker thread when it terminates.
     */
    public void unregisterWorkerThread(Thread thread) {
        workerThreads.remove(thread);
    }

    /**
     * Set the initial priority for a worker thread and register it.
     */
    public void setWorkerThreadPriority(Thread thread, WorkerType type) {
        if (!MetalConfig.threadPrioritiesEnabled) {
            return;
        }

        // Register first so the thread is included in any concurrent priority adjustment.
        registerWorkerThread(thread, type);
        int priority = getBasePriority(type);
        applyPriority(thread, priority);
    }

    /**
     * Update TPS and adjust all worker thread priorities if dynamic adjustment is enabled.
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
     * Dynamically adjust all registered worker thread priorities based on current load.
     */
    private void adjustPrioritiesForLoad() {
        if (currentTPS < TPS_CRITICAL) {
            LOGGER.info("TPS critical ({} < {}), boosting main thread and throttling workers",
                    String.format("%.1f", currentTPS), TPS_CRITICAL);
            applyPriority(mainThread, Thread.MAX_PRIORITY);
            for (Map.Entry<Thread, WorkerType> entry : workerThreads.entrySet()) {
                applyPriority(entry.getKey(), Thread.MIN_PRIORITY);
            }
        } else if (currentTPS < TPS_WARNING) {
            LOGGER.debug("TPS warning ({} < {}), reducing worker priorities",
                    String.format("%.1f", currentTPS), TPS_WARNING);
            for (Map.Entry<Thread, WorkerType> entry : workerThreads.entrySet()) {
                int reduced = Math.max(Thread.MIN_PRIORITY, getBasePriority(entry.getValue()) - 1);
                applyPriority(entry.getKey(), reduced);
            }
        } else {
            restoreNormalPriorities();
        }
    }

    /**
     * Restore all threads to their configured base priorities.
     */
    private void restoreNormalPriorities() {
        applyPriority(mainThread, MetalConfig.mainThreadPriority);
        for (Map.Entry<Thread, WorkerType> entry : workerThreads.entrySet()) {
            applyPriority(entry.getKey(), getBasePriority(entry.getValue()));
        }
    }

    /**
     * Get the configured base priority for a worker type, clamped to valid JVM range.
     */
    public int getRecommendedPriority(WorkerType type) {
        if (!MetalConfig.threadPrioritiesEnabled) {
            return Thread.NORM_PRIORITY;
        }

        int base = getBasePriority(type);
        if (underLoad && MetalConfig.dynamicPriorityAdjustment) {
            if (currentTPS < TPS_CRITICAL) {
                base = Math.max(Thread.MIN_PRIORITY, base - 2);
            } else if (currentTPS < TPS_WARNING) {
                base = Math.max(Thread.MIN_PRIORITY, base - 1);
            }
        }
        return base;
    }

    private int getBasePriority(WorkerType type) {
        int priority = switch (type) {
            case CHUNK_LOADING -> MetalConfig.chunkLoadingPriority;
            case ENTITY_PROCESSING -> MetalConfig.entityProcessingPriority;
            case TILE_ENTITY -> Math.max(Thread.MIN_PRIORITY, MetalConfig.entityProcessingPriority - 1);
            case BACKGROUND -> Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 2);
        };
        return Math.min(Thread.MAX_PRIORITY, Math.max(Thread.MIN_PRIORITY, priority));
    }

    private void applyPriority(Thread thread, int priority) {
        try {
            thread.setPriority(Math.min(Thread.MAX_PRIORITY, Math.max(Thread.MIN_PRIORITY, priority)));
        } catch (SecurityException e) {
            LOGGER.warn("Failed to set thread priority for {}: {}", thread.getName(), e.getMessage());
        }
    }

    /**
     * Get current TPS.
     */
    public double getCurrentTPS() {
        return currentTPS;
    }

    /**
     * Check if server is under load.
     */
    public boolean isUnderLoad() {
        return underLoad;
    }

    /**
     * Worker thread types for priority classification.
     */
    public enum WorkerType {
        CHUNK_LOADING,
        ENTITY_PROCESSING,
        TILE_ENTITY,
        BACKGROUND
    }
}
