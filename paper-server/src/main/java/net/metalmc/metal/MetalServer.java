package net.metalmc.metal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central lifecycle manager for MetalMC components.
 *
 * <p>Initialise once at server startup via {@link #init()} and shut down via
 * {@link #shutdown()}. All subsystems are accessible through the singleton
 * instance returned by {@link #getInstance()}.
 */
public final class MetalServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetalServer.class);
    private static volatile MetalServer instance;

    private final ThreadPriorityManager threadPriorityManager;
    private final AsyncChunkLoader chunkLoader;
    private final AsyncEntityProcessor entityProcessor;
    private final AsyncTileEntityProcessor tileEntityProcessor;

    private MetalServer() {
        this.threadPriorityManager = new ThreadPriorityManager(Thread.currentThread());

        this.chunkLoader = MetalConfig.asyncChunkLoadingEnabled
                ? new AsyncChunkLoader(threadPriorityManager)
                : null;

        this.entityProcessor = MetalConfig.asyncEntityProcessingEnabled
                ? new AsyncEntityProcessor(threadPriorityManager)
                : null;

        this.tileEntityProcessor = MetalConfig.asyncTileEntitiesEnabled
                ? new AsyncTileEntityProcessor(threadPriorityManager)
                : null;

        LOGGER.info("MetalMC subsystems initialized");
    }

    /**
     * Initialise all MetalMC subsystems. Must be called after {@link MetalConfig#init}.
     * Safe to call from multiple threads; only the first call has effect.
     */
    public static void init() {
        if (instance == null) {
            synchronized (MetalServer.class) {
                if (instance == null) {
                    instance = new MetalServer();
                }
            }
        } else {
            LOGGER.warn("MetalServer.init() called more than once – ignoring");
        }
    }

    /**
     * Returns the singleton instance, throwing if {@link #init()} has not been called.
     */
    public static MetalServer getInstance() {
        MetalServer inst = instance;
        if (inst == null) {
            throw new IllegalStateException("MetalServer has not been initialised");
        }
        return inst;
    }

    /**
     * Gracefully shut down all subsystems. Safe to call multiple times.
     */
    public static void shutdown() {
        MetalServer inst = instance;
        if (inst == null) {
            return;
        }
        LOGGER.info("Shutting down MetalMC subsystems...");
        if (inst.chunkLoader != null) inst.chunkLoader.shutdown();
        if (inst.entityProcessor != null) inst.entityProcessor.shutdown();
        if (inst.tileEntityProcessor != null) inst.tileEntityProcessor.shutdown();
        instance = null;
        LOGGER.info("MetalMC subsystems stopped");
    }

    // -------------------------------------------------------------------------
    // Subsystem accessors
    // -------------------------------------------------------------------------

    public ThreadPriorityManager getThreadPriorityManager() {
        return threadPriorityManager;
    }

    /** Returns the chunk loader, or {@code null} if async chunk loading is disabled. */
    public AsyncChunkLoader getChunkLoader() {
        return chunkLoader;
    }

    /** Returns the entity processor, or {@code null} if async entity processing is disabled. */
    public AsyncEntityProcessor getEntityProcessor() {
        return entityProcessor;
    }

    /** Returns the tile entity processor, or {@code null} if async tile entities are disabled. */
    public AsyncTileEntityProcessor getTileEntityProcessor() {
        return tileEntityProcessor;
    }
}
