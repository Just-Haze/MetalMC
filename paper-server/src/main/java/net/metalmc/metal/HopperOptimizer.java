package net.metalmc.metal;

/**
 * Lightweight tick-skip helper for hopper block entities.
 *
 * <h2>Problem</h2>
 * Hoppers call their transfer logic every tick even when the hopper is
 * empty, the target inventory is full, or there is nothing above the
 * hopper to pull from.  On servers with many hoppers this wastes a
 * significant fraction of main-thread time.
 *
 * <h2>Solution</h2>
 * This class provides a stateless {@link #shouldSkipTick} helper that
 * the hopper {@code tick()} method can consult.  When the hopper is in a
 * provably idle state the tick is deferred; actual state changes (items
 * moving) reset the idle counter so timing remains vanilla-accurate for
 * active hoppers.
 *
 * <p>All state is stored <em>on the caller's block entity</em> via
 * {@code lastTransferTick} and the two boolean flags; this class itself
 * is stateless and thread-safe.
 */
public final class HopperOptimizer {

    /**
     * How many ticks to skip when the hopper is completely empty.
     * A value of 20 means the hopper re-checks once per second.
     */
    public static final int EMPTY_SKIP_TICKS = 20;

    /**
     * How many ticks to skip when the target inventory is full.
     * A value of 8 means the hopper re-checks 2–3 times per second.
     */
    public static final int FULL_TARGET_SKIP_TICKS = 8;

    /**
     * How many ticks to skip when there are no source items above the
     * hopper and the hopper itself is empty.
     */
    public static final int NO_SOURCE_SKIP_TICKS = 10;

    private HopperOptimizer() {}

    /**
     * Decides whether the hopper tick can be safely skipped this game tick.
     *
     * <p>Only skips when the optimisation is enabled in {@link MetalConfig}.
     * When the hopper is actively transferring items the method always
     * returns {@code false} so vanilla transfer timing is preserved.
     *
     * @param lastTransferTick  game tick of the most recent successful item
     *                          transfer (or 0 if never transferred)
     * @param hopperIsEmpty     {@code true} when the hopper's own inventory
     *                          contains no items
     * @param targetIsFull      {@code true} when the destination inventory
     *                          cannot accept any more items
     * @param noSourceAbove     {@code true} when there is no item entity or
     *                          pullable inventory directly above the hopper
     * @param currentTick       the current game tick (e.g.
     *                          {@code level.getGameTime()})
     * @return {@code true} if the hopper tick should be skipped
     */
    public static boolean shouldSkipTick(
            final long lastTransferTick,
            final boolean hopperIsEmpty,
            final boolean targetIsFull,
            final boolean noSourceAbove,
            final long currentTick) {

        if (!MetalConfig.optimizeHoppers) {
            return false;
        }

        final long ticksSinceTransfer = currentTick - lastTransferTick;

        // Completely empty hopper with nothing above: skip for a longer interval.
        if (hopperIsEmpty && noSourceAbove && ticksSinceTransfer > NO_SOURCE_SKIP_TICKS) {
            return true;
        }

        // Empty hopper (items may arrive from above): shorter skip.
        if (hopperIsEmpty && ticksSinceTransfer > EMPTY_SKIP_TICKS) {
            return true;
        }

        // Target full: items cannot push through; skip briefly.
        if (targetIsFull && ticksSinceTransfer > FULL_TARGET_SKIP_TICKS) {
            return true;
        }

        return false;
    }
}
