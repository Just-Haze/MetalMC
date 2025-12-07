# ⚔️ MetalMC

> **High-Performance Minecraft Server Software**
>
> _Fork of PaperMC, targeting Minecraft 1.21.10_

MetalMC is a highly optimized, multithreaded Minecraft server implementation designed to squeeze every ounce of performance out of your hardware. By parallelizing heavy tasks and optimizing core math and logic, MetalMC delivers superior TPS and stability under load.

---

## ⚡ Key Features

### 🚀 Advanced Multithreading

MetalMC moves heavy lifting off the main thread to keep your server running at 20 TPS.

-   **Async Chunk Loading:** Smart priority system handles chunk IO on worker threads, prioritizing players.
-   **Async Entity Processing:** Pathfinding and collision checks run in parallel.
-   **Async Tile Entities:** Hoppers and furnaces calculate logic asynchronously.
-   **Dynamic Thread Priorities:** Automatically adjusts thread resources based on server load (TPS).

### ❄️ Zero-Waste Ticking

Core game loops are re-engineered for efficiency.

-   **Predictive Caching:** Pre-calcs environmental updates (weather, lightning) to skip expensive RNG.
-   **Optimized Chunk Ticking:** Eliminates redundant checks in the chunk loop.

### 🔢 Metal Math Engine

A custom, low-level math library built for Minecraft physics.

-   **L1 Cache Optimized:** 4KB lookup tables fit in CPU cache for instant access.
-   **Integer Arithmetic:** Replaces slow floating-point operations with CPU-native integer math.

---

## 🛠️ Configuration (`metal.yml`)

MetalMC generates a `metal.yml` file automatically on first launch.

### Sample Configuration

```yaml
optimizations:
    chunk-ticking: true # Toggle enhanced chunk ticking

multithreading:
    async-chunk-loading:
        enabled: true
        threads: 4 # Default: CPU Cores / 2
        prioritize-player-chunks: true

    async-entity-processing:
        enabled: true
        threads: 2

    async-tile-entities:
        enabled: true # Hoppers & Furnaces

    thread-priorities:
        enabled: true
        dynamic-adjustment: true # Auto-tune priorities based on TPS
```

---

## 📦 Building MetalMC

To build the server JAR from source:

1.  **Clone the repository**
2.  **Apply Patches:**
    ```bash
    ./gradlew applyPatches
    ```
3.  **Build JAR:**
    ```bash
    ./gradlew createMojmapBundlerJar
    ```
4.  **Find your JAR:**
    Located in `paper-server/build/libs/`

---

## ⚖️ License

Fork of [PaperMC](https://github.com/PaperMC/Paper). Licensed under GPLv3.
