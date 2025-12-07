# ⚡ MetalMC

> **High-Performance Server for Minecraft 1.21.10**

![Build Status](https://img.shields.io/badge/build-passing-brightgreen) ![Version](https://img.shields.io/badge/version-1.21.10-blue) ![License](https://img.shields.io/badge/license-GPLv3-orange)

MetalMC is a rebuilt, high-performance Minecraft server implementation designed for 1.21.10. It features a custom-built optimization engine that significantly reduces CPU usage and improves tick consistency under load.

---

## 🚀 Key Features

### 🧠 Smart Entity Activation

MetalMC includes a proprietary entity management system that intelligently scales AI processing based on player proximity.

-   **Dynamic Throttling:** Entities far from players automatically reduce their tick rate to save resources without affecting gameplay.
-   **Smart Logic:** Prioritizes active interactions while putting distant mobs into specific low-power states.
-   **Result:** Capable of handling significantly higher entity counts than standard implementations.

### ❄️ Enhanced Chunk Processing

We have re-engineered the core chunk ticking loop to eliminate wasted cycles.

-   **Predictive Caching:** Environmental updates (like lightning and weather) are pre-calculated to avoid expensive random number generation every tick.
-   **Zero-Waste Checks:** Redundant logic is stripped from the main loop, ensuring every CPU cycle contributes to gameplay.

### 🔢 Metal Math Engine

A custom, low-level math library built specifically for Minecraft's physics.

-   **L1 Cache Optimized:** Key lookup tables are compressed to 4KB to fit entirely within CPU L1 cache, granting near-instant access for physics calculations.
-   **Integer Arithmetic:** Replaces slow floating-point operations with CPU-native integer math for maximum efficiency.

---

## 🛠️ Configuration (`metal.yml`)

MetalMC generates a `metal.yml` file for easy configuration.

```yaml
optimizations:
    # Enable enhanced chunk processing
    chunk-ticking: true

    # Enable AI throttling
    ai-throttling: true

    # Smart Entity Activation Settings
    dab:
        enabled: true
        start-distance: 12 # Start throttling at 12 blocks
        max-tick-freq: 20 # Max throttle: 1 tick per second
        activation-dist-mod: 8 # Throttling curve aggressiveness
```

## 📦 Installation

1. Download the latest `metalmc-1.21.10-R0.1-SNAPSHOT.jar` from the [Releases](https://github.com/Just-Haze/MetalMC/releases) page.
2. Run it just like any server jar:
    ```bash
    java -jar metalmc-1.21.10-R0.1-SNAPSHOT.jar --nogui
    ```
3. Edit `metal.yml` to tune your performance settings.

---

_MetalMC: Built for Speed._
