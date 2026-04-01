package net.metalmc.metal;

/**
 * Hash-based work distributor that spreads expensive per-tick operations
 * across multiple ticks.
 *
 * <h2>Problem</h2>
 * Minecraft's main tick loop processes every entity, block entity, and
 * spawner <em>every single tick</em>.  When hundreds of the same object
 * type are present, all of their expensive checks (pathfinding, spawner
 * eligibility, random-tick candidates) pile up in one 50 ms window,
 * causing lag spikes.
 *
 * <h2>Solution</h2>
 * Instead of uniform execution, assign each object a deterministic
 * "bucket" based on its unique ID.  Only the bucket whose number matches
 * {@code currentTick % buckets} is processed on any given tick.
 * Over {@code buckets} ticks every object is processed exactly once —
 * the same rate as before, but with the cost spread evenly.
 *
 * <h2>Vanilla safety</h2>
 * This scheduler is suitable for operations where a delay of up to
 * {@code buckets-1} ticks (typically 2–4 ticks, 100–200 ms) is
 * imperceptible to players: mob-spawner eligibility checks, non-urgent
 * random-ticks, and distant-entity AI.  It must <em>not</em> be used
 * for time-critical mechanics (redstone, explosions, player interaction).
 */
public final class TickScheduler {

    private TickScheduler() {}

    /**
     * Returns {@code true} if the object with the given {@code id} should
     * be processed on {@code currentTick} when using a {@code buckets}-wide
     * distribution window.
     *
     * <p>Example usage:
     * <pre>{@code
     * // In a spawner's tick() method:
     * if (!TickScheduler.shouldProcess(spawner.blockEntityId, level.getGameTime(), 4)) {
     *     return; // defer to the next scheduled bucket tick
     * }
     * // … expensive eligibility check …
     * }</pre>
     *
     * @param id          a stable unique integer for the object
     *                    (entity ID, block position hash, etc.)
     * @param currentTick the current game time / tick count
     * @param buckets     number of buckets; must be ≥ 1.
     *                    Recommended values: 2, 4, or 8.
     * @return {@code true} when the object should execute this tick
     */
    public static boolean shouldProcess(final int id, final long currentTick, final int buckets) {
        if (buckets <= 1) {
            return true;
        }
        // Spread entities with the same ID modulus across different offsets
        // to avoid all IDs in the same range landing in bucket 0 simultaneously.
        final int bucket = (id & Integer.MAX_VALUE) % buckets;
        return (currentTick % buckets) == bucket;
    }

    /**
     * Overload accepting a {@code long} entity/block id.
     *
     * @param id          a stable unique long for the object
     * @param currentTick the current game time / tick count
     * @param buckets     number of buckets; must be ≥ 1
     * @return {@code true} when the object should execute this tick
     */
    public static boolean shouldProcess(final long id, final long currentTick, final int buckets) {
        if (buckets <= 1) {
            return true;
        }
        final long bucket = (id & Long.MAX_VALUE) % buckets;
        return (currentTick % buckets) == bucket;
    }

    /**
     * Convenience method using the default bucket count from
     * {@link MetalConfig#tickSchedulerBuckets}.
     *
     * @param id          stable unique integer for the object
     * @param currentTick current game time
     * @return {@code true} when the object should execute this tick
     */
    public static boolean shouldProcess(final int id, final long currentTick) {
        return shouldProcess(id, currentTick, MetalConfig.tickSchedulerBuckets);
    }
}
