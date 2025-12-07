# Implementation Plan - "Level 0" Optimizations for MetalMC

This plan outlines the steps to implement aggressive, low-level optimizations in the MetalMC fork of Paper.

## Goal Description

Implement "Level 0" style optimizations focusing on removing overhead from critical math operations and game logic. This includes:

1.  **Fast Math**: Replacing vanilla `Mth` trigonometric functions with faster, approximated, or hardware-accelerated versions (using libm or look-up tables where appropriate, essentially bypassing strict strict math for speed).
2.  **Entity Logic Throttling**: Aggressively throttling AI goals for entities to reduce tick time impact, effectively "de-prioritizing" mobs in favor of overall performance.

These changes are "aggressive" because they may slightly alter vanilla behavior (e.g., slight math inaccuracies, dumber mobs) in exchange for raw speed.

## User Review Required

> [!WARNING] > **Math Accuracy**: The "Fast Math" changes will use approximations or JNI calls that might result in very subtle coordinate/physics differences compared to Vanilla. This is usually imperceptible but technically "breaking".

> [!WARNING] > **Mob Behavior**: Throttling AI goals means mobs might react slightly slower or seem less "intelligent" in crowded situations.

## Proposed Changes

### Paper-Server

#### [MODIFY] [Mth.java](file:///c:/Users/skibidi/.gemini/antigravity/scratch/fork_of_papermc/paper-server/src/minecraft/java/net/minecraft/util/Mth.java)

-   Replace `sin`, `cos`, `sqrt` with faster implementations (e.g., from `FastMath` libraries or aggressive look-up tables if not already present, or Unsafe/Native alternatives).
-   _Note_: Paper already optimizes some of this, so we will look for "aggressive" variants often found in modded environments (like "Riven's sin/cos" or LibM).

#### [MODIFY] [GoalSelector.java](file:///c:/Users/skibidi/.gemini/antigravity/scratch/fork_of_papermc/paper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/GoalSelector.java)

-   Implement a "tick skipping" mechanism where lower-priority goals are checked less frequently.

## Verification Plan

### Automated Tests

-   Run `./gradlew build` to ensure compilation.
-   Run a local server and observe startup logs for any critical math errors.
-   Use `spark` (integrated in Paper) to verify lower CPU usage in heavy math scenarios (like TNT explosions or massive entity crowds).

### Manual Verification

-   **Math**: Spawn entities and verify physics (projectiles, movement) don't look "glitchy" or weird.
-   **AI**: Spawn a large group of zombies and verify they still track the player, even if slightly "dumber".
