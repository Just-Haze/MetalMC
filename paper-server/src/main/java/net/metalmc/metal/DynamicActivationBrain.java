package net.metalmc.metal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * Dynamic Activation of Brain (DAB) — throttles entity AI based on
 * distance from the nearest non-spectator player.
 *
 * Entities within {@code dabStartDistance} blocks always tick their brain
 * every game tick. Beyond that distance the brain tick interval grows
 * linearly with distance, capped at {@code dabMaxTickFreq}.
 *
 * This is vanilla-safe: only AI goal scheduling is affected. Entities
 * still receive world updates (gravity, fire, knockback, etc.) normally.
 *
 * Inspired by the "DAB" optimisation in the Pufferfish fork; this is an
 * independent implementation written for MetalMC.
 */
public final class DynamicActivationBrain {

    private DynamicActivationBrain() {}

    /**
     * Returns {@code true} when the entity's brain should be ticked on
     * the current server tick.
     *
     * <p>Call this at the start of the entity-brain / goal-selector tick
     * and skip the brain update when it returns {@code false}.
     *
     * @param entity the mob whose brain tick is being evaluated
     * @return {@code true} if the brain should tick this game tick
     */
    public static boolean shouldTickBrain(final Entity entity) {
        if (!MetalConfig.dabEnabled) {
            return true;
        }

        // Passengers/vehicles interact with players directly — always tick.
        if (entity.isPassenger() || entity.isVehicle()) {
            return true;
        }

        // Only Mob subclasses have a brain / goal system.
        if (!(entity instanceof Mob)) {
            return true;
        }

        final ServerLevel level = (ServerLevel) entity.level();
        final List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            // No players: skip AI entirely to save CPU.
            return false;
        }

        final double ex = entity.getX();
        final double ey = entity.getY();
        final double ez = entity.getZ();

        double nearestDistSq = Double.MAX_VALUE;
        for (int i = 0, len = players.size(); i < len; i++) {
            final ServerPlayer player = players.get(i);
            if (player.isSpectator()) {
                continue;
            }
            final double dx = player.getX() - ex;
            final double dy = player.getY() - ey;
            final double dz = player.getZ() - ez;
            final double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
            }
        }

        // All remaining players are spectators.
        if (nearestDistSq == Double.MAX_VALUE) {
            return false;
        }

        final int startDist = MetalConfig.dabStartDistance;
        final double startDistSq = (double) startDist * startDist;

        // Within activation radius: always tick.
        if (nearestDistSq <= startDistSq) {
            return true;
        }

        // Compute a throttle interval that scales with distance.
        // interval = clamp(1 + (dist - startDist) / activationDistMod, 1, maxTickFreq)
        final double distance = Math.sqrt(nearestDistSq);
        final int maxFreq = MetalConfig.dabMaxTickFreq;
        final double activationDistMod = MetalConfig.dabActivationDistMod;

        final int interval = (int) Math.min(maxFreq,
                1.0 + (distance - startDist) / activationDistMod);

        // Spread load by staggering each entity based on its unique ID.
        // Use bitmask-safe modulo to avoid issues with negative IDs.
        final int offset = (entity.getId() & Integer.MAX_VALUE) % Math.max(1, interval);
        return ((entity.tickCount + offset) % Math.max(1, interval)) == 0;
    }
}
