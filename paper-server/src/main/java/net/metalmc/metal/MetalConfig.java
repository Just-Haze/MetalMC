package net.metalmc.metal;

import com.google.common.base.Throwables;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

public class MetalConfig {
    private static File CONFIG_FILE;
    private static final String HEADER = "MetalMC Configuration File\n";
    private static final Logger LOGGER = Logger.getLogger("MetalMC");

    public static YamlConfiguration config;
    public static int version;
    public static boolean verbose;

    public static void init(File configFile) {
        CONFIG_FILE = configFile;
        config = new YamlConfiguration();
        try {
            config.load(CONFIG_FILE);
        } catch (java.io.FileNotFoundException ignored) {
            // Config does not exist yet; it will be created with defaults.
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Could not load " + CONFIG_FILE + ", using defaults", ex);
        }
        config.options().header(HEADER);
        config.options().copyDefaults(true);

        version = getInt("config-version", 1);
        set("config-version", 1);

        readConfig(MetalConfig.class, null);
    }

    protected static void set(String path, Object val) {
        config.addDefault(path, val);
        config.set(path, val);
    }

    protected static boolean getBoolean(String path, boolean def) {
        config.addDefault(path, def);
        return config.getBoolean(path, def);
    }

    protected static int getInt(String path, int def) {
        config.addDefault(path, def);
        return config.getInt(path, def);
    }

    protected static double getDouble(String path, double def) {
        config.addDefault(path, def);
        return config.getDouble(path, def);
    }

    protected static <T> List<T> getList(String path, List<T> def) {
        config.addDefault(path, def);
        @SuppressWarnings("unchecked")
        List<T> result = (List<T>) config.getList(path, def);
        return result;
    }

    public static boolean optimizeChunkTicking;

    // Multithreading Optimizations
    // Async Chunk Loading
    public static boolean asyncChunkLoadingEnabled;
    public static int chunkLoadingThreads;
    public static boolean prioritizePlayerChunks;
    public static int chunkLoadPriority;

    // Entity Processing
    public static boolean asyncEntityProcessingEnabled;
    public static boolean asyncPathfinding;
    public static boolean asyncCollisionDetection;
    public static int entityProcessingThreads;

    // Tile Entity Processing
    public static boolean asyncTileEntitiesEnabled;
    public static boolean asyncHoppers;
    public static boolean asyncFurnaces;
    public static int tileEntityThreads;

    // Thread Priorities
    public static boolean threadPrioritiesEnabled;
    public static int mainThreadPriority;
    public static int chunkLoadingPriority;
    public static int entityProcessingPriority;
    public static boolean dynamicPriorityAdjustment;

    // Advanced Scheduler
    public static boolean advancedSchedulerEnabled;
    public static boolean taskBatching;
    public static boolean autoAsyncDetection;
    public static int maxAsyncTasks;

    private static void general() {
        verbose = getBoolean("verbose", false);
    }

    private static void optimizations() {
        optimizeChunkTicking = getBoolean("optimizations.chunk-ticking", true);
    }

    private static void multithreading() {
        // Async Chunk Loading
        asyncChunkLoadingEnabled = getBoolean("multithreading.async-chunk-loading.enabled", true);
        chunkLoadingThreads = getInt("multithreading.async-chunk-loading.threads",
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
        prioritizePlayerChunks = getBoolean("multithreading.async-chunk-loading.prioritize-player-chunks", true);
        chunkLoadPriority = getInt("multithreading.async-chunk-loading.chunk-load-priority", 6);

        // Entity Processing
        asyncEntityProcessingEnabled = getBoolean("multithreading.async-entity-processing.enabled", true);
        asyncPathfinding = getBoolean("multithreading.async-entity-processing.async-pathfinding", true);
        asyncCollisionDetection = getBoolean("multithreading.async-entity-processing.async-collision-detection", false);
        entityProcessingThreads = getInt("multithreading.async-entity-processing.threads", 2);

        // Tile Entity Processing
        asyncTileEntitiesEnabled = getBoolean("multithreading.async-tile-entities.enabled", true);
        asyncHoppers = getBoolean("multithreading.async-tile-entities.async-hoppers", true);
        asyncFurnaces = getBoolean("multithreading.async-tile-entities.async-furnaces", true);
        tileEntityThreads = getInt("multithreading.async-tile-entities.threads", 2);

        // Thread Priorities
        threadPrioritiesEnabled = getBoolean("multithreading.thread-priorities.enabled", true);
        mainThreadPriority = getInt("multithreading.thread-priorities.main-thread-priority", Thread.MAX_PRIORITY);
        chunkLoadingPriority = getInt("multithreading.thread-priorities.chunk-loading-priority",
                Thread.NORM_PRIORITY + 1);
        entityProcessingPriority = getInt("multithreading.thread-priorities.entity-processing-priority",
                Thread.NORM_PRIORITY);
        dynamicPriorityAdjustment = getBoolean("multithreading.thread-priorities.dynamic-adjustment", true);

        // Advanced Scheduler
        advancedSchedulerEnabled = getBoolean("multithreading.advanced-scheduler.enabled", true);
        taskBatching = getBoolean("multithreading.advanced-scheduler.task-batching", true);
        autoAsyncDetection = getBoolean("multithreading.advanced-scheduler.auto-async-detection", true);
        maxAsyncTasks = getInt("multithreading.advanced-scheduler.max-async-tasks", 100);
    }

    static void readConfig(Class<?> clazz, Object instance) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (Modifier.isPrivate(method.getModifiers())) {
                if (method.getParameterTypes().length == 0 && method.getReturnType() == Void.TYPE) {
                    try {
                        method.setAccessible(true);
                        method.invoke(instance);
                    } catch (Exception ex) {
                        Throwables.propagate(ex);
                    }
                }
            }
        }

        try {
            config.save(CONFIG_FILE);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Could not save " + CONFIG_FILE, ex);
        }
    }
}
