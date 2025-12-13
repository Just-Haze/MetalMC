# ⚔️ MetalMC

> **The High-Performance Minecraft Server Interface**  
> _A cutting-edge fork of PaperMC, engineered for stability and speed._

![License](https://img.shields.io/badge/License-GPL%20v3-blue.svg) ![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.10-green.svg)

---

## 📖 Overview

**MetalMC** is a specialized Minecraft server implementation based on PaperMC, capable of handling higher player counts and complex gameplay mechanics with ease. By introducing advanced multithreading, optimizing core math operations, and refining the game loop, MetalMC significantly reduces tick latency and improves overall server responsiveness.

Designed for server owners who demand the absolute best performance, MetalMC retains full compatibility with the rich ecosystem of Bukkit, Spigot, and Paper plugins while delivering "metal-to-the-pedal" speeds.

---

## ✨ Features

### 🚀 Advanced Multithreading

Unlock the full potential of your hardware by offloading tasks from the main thread.

-   **Async Chunk Loading:** Prioritized, non-blocking chunk IO keeps players moving smoothly.
-   **Parallel Entity Processing:** Entity pathfinding and collisions logic run on worker threads.
-   **Async Tile Entities:** Heavy blocks like Hoppers and Furnaces update asynchronously.
-   **Dynamic Thread Management:** Automatically adjusts thread allocation based on real-time TPS.

### ❄️ Zero-Waste Ticking

Optimized game loops that respect your CPU cycles.

-   **Predictive Caching:** Pre-calculates environmental factors (weather, lightning) to minimize RNG overhead.
-   **Smart Chunk Ticking:** Eliminates redundant checks in critical loops.

### 🔢 Metal Math Engine

A bespoke component replacing standard libraries with high-performance alternatives.

-   **Integer Arithmetic:** Replaces slow floating-point operations with CPU-native integer math where possible.
-   **L1 Cache Optimization:** Micro-optimized lookup tables (4KB) for instant trigonometric access.

---

## 📥 Installation

1.  **Download:** Get the latest `metalmc.jar` from the [Releases](https://github.com/MetalMC/MetalMC/releases) page (if available) or build from source.
2.  **Environment:** Ensure you have **Java 21** or later installed.
3.  **Run:**
    ```bash
    java -Xms4G -Xmx4G -jar metalmc.jar
    ```
4.  **Enjoy:** Pure performance.

---

## ⚙️ Configuration

MetalMC generates a dedicated configuration file `metal.yml` on the first launch. You can toggle specific optimizations to suit your server's needs.

**Sample `metal.yml`:**

```yaml
optimizations:
    chunk-ticking: true # Enhanced chunk tick logic

multithreading:
    async-chunk-loading:
        enabled: true
        threads: 4 # Defaults to CPU Cores / 2
        prioritize-player-chunks: true

    async-entity-processing:
        enabled: true
        threads: 2

    async-tile-entities:
        enabled: true # Offloads Hoppers & Furnaces

    thread-priorities:
        enabled: true
        dynamic-adjustment: true # Auto-tunes priorities based on load
```

---

## 📦 Building from Source

To compile MetalMC yourself, follow these steps:

1.  **Clone the valid repository:**
    ```bash
    git clone https://github.com/MetalMC/MetalMC.git
    cd MetalMC
    ```
2.  **Apply Patches:**
    ```bash
    ./gradlew applyPatches
    ```
3.  **Build the JAR:**
    ```bash
    ./gradlew createMojmapBundlerJar
    ```
4.  **Locate Artifact:**
    The final JAR will be in `paper-server/build/libs/`.

---

## 🧩 Compatibility

MetalMC is designed to be a drop-in replacement for Paper.

-   **Plugins:** Compatible with Bukkit, Spigot, and Paper plugins.
-   **World Data:** standard Anvil/Region format. _Always backup your world before switching server software._

> [!NOTE]
> Some extremely invasive plugins that rely on exact vanilla behavior (NMS) or specific tick ordering _might_ conflict with async optimizations. If issues arise, try disabling specific async modules in `metal.yml`.

---

## ❓ FAQ

**Q: Is it safe for production?**  
A: Yes, MetalMC is stable. However, as with any performant fork, testing with your specific plugin stack is recommended.

**Q: Does it break Redstone?**  
A: No. Vanilla redstone behavior is preserved unless specific experimental toggles are enabled.

---

## ⚖️ License

MetalMC is a fork of [Paper](https://github.com/PaperMC/Paper), which is a fork of [Spigot](https://www.spigotmc.org/), based on [CraftBukkit](https://bukkit.org/).

Licensed under the [GPLv3 License](LICENSE.md).
