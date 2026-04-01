package net.metalmc.metal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Benchmark and correctness tests for {@link AsyncEntityProcessor},
 * {@link AsyncTileEntityProcessor}, and {@link AsyncChunkLoader}.
 *
 * <p>Tests verify:
 * <ul>
 *   <li>Processors initialise and shut down without errors.</li>
 *   <li>Async submission returns results within reasonable time limits.</li>
 *   <li>Statistics counters are updated correctly.</li>
 *   <li>Config-disabled paths short-circuit immediately (no unnecessary work).</li>
 *   <li>Throughput of async submissions stays above minimum acceptable rate.</li>
 * </ul>
 */
class AsyncProcessorBenchmarkTest {

    private ThreadPriorityManager priorityManager;
    private AsyncEntityProcessor entityProcessor;
    private AsyncTileEntityProcessor tileEntityProcessor;
    private AsyncChunkLoader chunkLoader;

    @BeforeEach
    void setUp() {
        // Wire up config defaults without a live Minecraft server.
        MetalConfig.threadPrioritiesEnabled = true;
        MetalConfig.mainThreadPriority = Thread.NORM_PRIORITY;
        MetalConfig.chunkLoadingPriority = Thread.NORM_PRIORITY;
        MetalConfig.entityProcessingPriority = Thread.NORM_PRIORITY;
        MetalConfig.dynamicPriorityAdjustment = false;

        MetalConfig.asyncEntityProcessingEnabled = true;
        MetalConfig.asyncPathfinding = true;
        MetalConfig.asyncCollisionDetection = false;
        MetalConfig.entityProcessingThreads = 2;

        MetalConfig.asyncTileEntitiesEnabled = true;
        MetalConfig.asyncHoppers = true;
        MetalConfig.asyncFurnaces = true;
        MetalConfig.tileEntityThreads = 2;

        MetalConfig.asyncChunkLoadingEnabled = true;
        MetalConfig.chunkLoadingThreads = 2;
        MetalConfig.prioritizePlayerChunks = true;

        priorityManager = new ThreadPriorityManager(Thread.currentThread());
        entityProcessor = new AsyncEntityProcessor(priorityManager);
        tileEntityProcessor = new AsyncTileEntityProcessor(priorityManager);
        chunkLoader = new AsyncChunkLoader(priorityManager);
    }

    @AfterEach
    void tearDown() {
        entityProcessor.shutdown();
        tileEntityProcessor.shutdown();
        chunkLoader.shutdown();
        Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
    }

    // -----------------------------------------------------------------------
    // AsyncEntityProcessor — correctness
    // -----------------------------------------------------------------------

    @Test
    void entityProcessorInitialisesWithoutError() {
        AsyncEntityProcessor.ProcessingStatistics stats = entityProcessor.getStatistics();
        assertNotNull(stats);
        assertEquals(0, stats.totalPathfinds());
        assertEquals(0, stats.activePathfinds());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void calculatePathAsyncReturnsWithinTimeout() throws Exception {
        // calculatePath() is currently a stub that returns null – we verify the
        // future completes without hanging and returns null (the sync-fallback signal).
        CompletableFuture<net.minecraft.world.level.pathfinder.Path> future =
                entityProcessor.calculatePathAsync(null, null);
        net.minecraft.world.level.pathfinder.Path result = future.get(2, TimeUnit.SECONDS);
        assertNull(result, "Stub path calculation should return null to signal sync fallback");
    }

    @Test
    void entityProcessorStatisticsIncrementOnSubmission() throws Exception {
        entityProcessor.calculatePathAsync(null, null).get(2, TimeUnit.SECONDS);
        assertEquals(1, entityProcessor.getStatistics().totalPathfinds());
    }

    @Test
    void entityProcessorDisabledByConfigShortCircuits() throws Exception {
        MetalConfig.asyncEntityProcessingEnabled = false;
        CompletableFuture<net.minecraft.world.level.pathfinder.Path> future =
                entityProcessor.calculatePathAsync(null, null);
        // Should complete immediately with a pre-completed future (no thread pool submission)
        assertTrue(future.isDone(), "Disabled async processing should return a pre-completed future");
        assertNull(future.get());
    }

    // -----------------------------------------------------------------------
    // AsyncTileEntityProcessor — correctness
    // -----------------------------------------------------------------------

    @Test
    void tileEntityProcessorInitialisesWithoutError() {
        AsyncTileEntityProcessor.TileEntityStatistics stats = tileEntityProcessor.getStatistics();
        assertNotNull(stats);
        assertEquals(0, stats.totalProcessed());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void hopperAsyncReturnsWithinTimeout() throws Exception {
        AsyncTileEntityProcessor.HopperTransferResult result =
                tileEntityProcessor.processHopperAsync().get(2, TimeUnit.SECONDS);
        assertNotNull(result);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void furnaceAsyncReturnsWithinTimeout() throws Exception {
        AsyncTileEntityProcessor.FurnaceSmeltResult result =
                tileEntityProcessor.processFurnaceAsync().get(2, TimeUnit.SECONDS);
        assertNotNull(result);
    }

    @Test
    void tileEntityProcessorDisabledByConfigShortCircuits() {
        MetalConfig.asyncTileEntitiesEnabled = false;
        CompletableFuture<AsyncTileEntityProcessor.HopperTransferResult> future =
                tileEntityProcessor.processHopperAsync();
        assertTrue(future.isDone(), "Disabled async tile entities should return a pre-completed future");
    }

    @Test
    void tileEntityStatisticsIncrementOnSubmission() throws Exception {
        tileEntityProcessor.processHopperAsync().get(2, TimeUnit.SECONDS);
        tileEntityProcessor.processFurnaceAsync().get(2, TimeUnit.SECONDS);
        assertEquals(2, tileEntityProcessor.getStatistics().totalProcessed());
    }

    // -----------------------------------------------------------------------
    // AsyncChunkLoader — correctness
    // -----------------------------------------------------------------------

    @Test
    void chunkLoaderInitialisesWithoutError() {
        AsyncChunkLoader.LoadStatistics stats = chunkLoader.getStatistics();
        assertNotNull(stats);
        assertEquals(0, stats.totalLoadsProcessed());
        assertEquals(0, stats.activeLoads());
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.SECONDS)
    void chunkLoadAsyncCompletesWithinTimeout() throws Exception {
        chunkLoader.loadChunkAsync(null, null, null, true).get(2, TimeUnit.SECONDS);
        assertEquals(1, chunkLoader.getStatistics().totalLoadsProcessed());
    }

    @Test
    void chunkLoaderPlayerRequestCountsAreSeparated() throws Exception {
        chunkLoader.loadChunkAsync(null, null, null, true).get(2, TimeUnit.SECONDS);
        chunkLoader.loadChunkAsync(null, null, null, false).get(2, TimeUnit.SECONDS);
        AsyncChunkLoader.LoadStatistics stats = chunkLoader.getStatistics();
        assertEquals(2, stats.totalLoadsProcessed());
        assertEquals(1, stats.playerRequestedLoads());
    }

    @Test
    void chunkLoaderDisabledByConfigShortCircuits() {
        MetalConfig.asyncChunkLoadingEnabled = false;
        CompletableFuture<Void> future = chunkLoader.loadChunkAsync(null, null, null, true);
        assertTrue(future.isDone(), "Disabled chunk loading should return a pre-completed future");
    }

    // -----------------------------------------------------------------------
    // Performance (throughput) gates
    // -----------------------------------------------------------------------

    /**
     * Submit 500 async hopper operations and verify they all finish within 5 seconds.
     * This ensures the thread pool is not deadlocked and can sustain a basic workload.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void tileEntityProcessorSustainsThroughputUnderLoad() throws Exception {
        int count = 500;
        @SuppressWarnings("unchecked")
        CompletableFuture<AsyncTileEntityProcessor.HopperTransferResult>[] futures =
                new CompletableFuture[count];

        for (int i = 0; i < count; i++) {
            futures[i] = tileEntityProcessor.processHopperAsync();
        }

        CompletableFuture.allOf(futures).get(5, TimeUnit.SECONDS);

        assertEquals(count, tileEntityProcessor.getStatistics().totalProcessed(),
                "All submitted tasks should be counted in statistics");
    }

    /**
     * Submit 500 async chunk loads and verify they all finish within 5 seconds.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void chunkLoaderSustainsThroughputUnderLoad() throws Exception {
        int count = 500;
        @SuppressWarnings("unchecked")
        CompletableFuture<Void>[] futures = new CompletableFuture[count];

        for (int i = 0; i < count; i++) {
            futures[i] = chunkLoader.loadChunkAsync(null, null, null, i % 2 == 0);
        }

        CompletableFuture.allOf(futures).get(5, TimeUnit.SECONDS);

        AsyncChunkLoader.LoadStatistics stats = chunkLoader.getStatistics();
        assertEquals(count, stats.totalLoadsProcessed());
        assertEquals(count / 2, stats.playerRequestedLoads());
    }

    /**
     * Measure the average latency of a single async hopper submission.
     * Should be well under 1 ms per call on average (thread pool overhead only).
     */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void asyncSubmissionLatencyIsAcceptable() throws Exception {
        int warmUp = 200;
        int measured = 1000;

        // Warm up the thread pool
        for (int i = 0; i < warmUp; i++) {
            tileEntityProcessor.processHopperAsync().get(1, TimeUnit.SECONDS);
        }

        long start = System.nanoTime();
        @SuppressWarnings("unchecked")
        CompletableFuture<AsyncTileEntityProcessor.HopperTransferResult>[] futures =
                new CompletableFuture[measured];
        for (int i = 0; i < measured; i++) {
            futures[i] = tileEntityProcessor.processHopperAsync();
        }
        CompletableFuture.allOf(futures).get(5, TimeUnit.SECONDS);
        long elapsedNs = System.nanoTime() - start;

        long averageNs = elapsedNs / measured;
        // Average async round-trip should be under 5 ms
        assertTrue(averageNs < 5_000_000,
                String.format("Average async submission latency was %d ns (limit: 5,000,000 ns)", averageNs));
    }
}
