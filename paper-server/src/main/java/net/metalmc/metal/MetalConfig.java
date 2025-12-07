package net.metalmc.metal;

import com.google.common.base.Throwables;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

public class MetalConfig {
    private static File CONFIG_FILE;
    private static final String HEADER = "MetalMC Configuration File\n";

    public static YamlConfiguration config;
    public static int version;
    public static boolean verbose;

    public static void init(File configFile) {
        CONFIG_FILE = configFile;
        config = new YamlConfiguration();
        try {
            config.load(CONFIG_FILE);
        } catch (Exception ignored) {
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
        return config.getBoolean(path, config.getBoolean(path));
    }

    protected static int getInt(String path, int def) {
        config.addDefault(path, def);
        return config.getInt(path, config.getInt(path));
    }

    protected static double getDouble(String path, double def) {
        config.addDefault(path, def);
        return config.getDouble(path, config.getDouble(path));
    }

    protected static <T> List<T> getList(String path, T def) {
        config.addDefault(path, def);
        return (List<T>) config.getList(path, config.getList(path));
    }

    public static boolean optimizeChunkTicking;
    public static boolean throttleAI;

    // MetalMC - DAB
    public static boolean dearEnabled;
    public static int startDistanceSquared;
    public static int maximumActivationPrio;
    public static int activationDistanceMod;

    private static void optimizations() {
        optimizeChunkTicking = getBoolean("optimizations.chunk-ticking", true);
        throttleAI = getBoolean("optimizations.ai-throttling", true);

        // DAB
        dearEnabled = getBoolean("optimizations.dab.enabled", true);
        startDistanceSquared = getInt("optimizations.dab.start-distance", 12);
        startDistanceSquared = startDistanceSquared * startDistanceSquared; // Square it
        maximumActivationPrio = getInt("optimizations.dab.max-tick-freq", 20);
        activationDistanceMod = getInt("optimizations.dab.activation-dist-mod", 8);
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
            Bukkit.getLogger().severe("Could not save " + CONFIG_FILE);
            ex.printStackTrace();
        }
    }
}
