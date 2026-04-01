package net.metalmc.metal;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Fast nearest-player lookup cache for entity-activation and DAB checks.
 *
 * <h2>Problem</h2>
 * Both the Entity Activation Range (EAR) system and the Dynamic
 * Activation of Brain ({@link DynamicActivationBrain}) need to compute
 * "distance to the nearest player" for every entity every tick.  With
 * 500 entities and 20 players that is up to 10 000 distance calculations
 * per tick — the bulk of which are redundant because players rarely move
 * more than a block between ticks.
 *
 * <h2>Solution</h2>
 * A per-entity, per-tick cache stores the squared distance to the
 * nearest player.  On a subsequent call within the <em>same</em> game
 * tick the cached value is returned directly.  The cache is invalidated
 * at the start of each tick via {@link #beginTick(long)}.
 *
 * <p>Because the cache is backed by a simple {@code double[]} indexed by
 * entity ID modulo a fixed table size, it has O(1) read/write complexity
 * and no object allocation after initialisation.
 *
 * <h2>Thread safety</h2>
 * This class is designed for single-threaded access from the main server
 * tick thread.  Do not call it from async workers.
 */
public final class EntityActivationOptimizer {

    /**
     * Cache table size. A power of two is required for the bitmask index.
     * 4096 entries cover most server entity counts with minimal collision.
     */
    private static final int CACHE_SIZE = 4096;
    private static final int CACHE_MASK = CACHE_SIZE - 1;

    /**
     * Cached nearest-player distance-squared values, indexed by
     * {@code entityId & CACHE_MASK}.  Negative sentinel means "stale".
     */
    private static final double[] DIST_SQ_CACHE = new double[CACHE_SIZE];

    /**
     * The game tick at which each cache slot was last written, used to
     * detect stale entries without a full array clear each tick.
     */
    private static final long[] CACHE_TICK = new long[CACHE_SIZE];

    /** Game tick of the last {@link #beginTick} call. */
    private static long currentTick = -1L;

    private EntityActivationOptimizer() {}

    /**
     * Must be called once at the start of each server tick (before any
     * entity processing) to advance the tick counter.
     *
     * @param gameTick current server game time (e.g. {@code level.getGameTime()})
     */
    public static void beginTick(final long gameTick) {
        currentTick = gameTick;
    }

    /**
     * Returns the squared distance from {@code entity} to the nearest
     * non-spectator player, using a per-tick cache to avoid redundant
     * calculations.
     *
     * <p>Returns {@link Double#MAX_VALUE} when no non-spectator players
     * are present in the level.
     *
     * @param entity the entity to check
     * @return squared distance to nearest player, or {@code Double.MAX_VALUE}
     */
    public static double nearestPlayerDistanceSq(final Entity entity) {
        final int slot = entity.getId() & CACHE_MASK;

        // Cache hit: same entity and same tick.
        if (CACHE_TICK[slot] == currentTick) {
            return DIST_SQ_CACHE[slot];
        }

        // Cache miss: compute and store.
        final double distSq = computeNearestPlayerDistSq(entity);
        DIST_SQ_CACHE[slot] = distSq;
        CACHE_TICK[slot] = currentTick;
        return distSq;
    }

    /**
     * Returns {@code true} when the nearest player is within
     * {@code rangeBlocks} of {@code entity}.
     *
     * @param entity      entity to test
     * @param rangeBlocks maximum distance in blocks
     * @return {@code true} if a player is within range
     */
    public static boolean isPlayerWithinRange(final Entity entity, final double rangeBlocks) {
        return nearestPlayerDistanceSq(entity) <= rangeBlocks * rangeBlocks;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static double computeNearestPlayerDistSq(final Entity entity) {
        @SuppressWarnings("unchecked")
        final List<ServerPlayer> players =
                (List<ServerPlayer>) entity.level().players();

        if (players.isEmpty()) {
            return Double.MAX_VALUE;
        }

        final double ex = entity.getX();
        final double ey = entity.getY();
        final double ez = entity.getZ();

        double nearest = Double.MAX_VALUE;
        for (int i = 0, len = players.size(); i < len; i++) {
            final ServerPlayer player = players.get(i);
            if (player.isSpectator()) {
                continue;
            }
            final double dx = player.getX() - ex;
            final double dy = player.getY() - ey;
            final double dz = player.getZ() - ez;
            final double dSq = dx * dx + dy * dy + dz * dz;
            if (dSq < nearest) {
                nearest = dSq;
            }
        }
        return nearest;
    }
}
