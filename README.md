# MetalMC

> **Forged for Performance. Level 0 Optimized.**

![License](https://img.shields.io/github/license/Just-Haze/MetalMC?style=for-the-badge&color=252525)
![Stars](https://img.shields.io/github/stars/Just-Haze/MetalMC?style=for-the-badge&color=252525)
![Issues](https://img.shields.io/github/issues/Just-Haze/MetalMC?style=for-the-badge&color=252525)

---

**MetalMC** is a specialized fork of [Paper](https://github.com/PaperMC/Paper) 1.21.10 aimed at **extreme server performance**.

Unlike general-purpose forks, MetalMC focuses on "Level 0" optimizations—changes that target hardware efficiency (CPU Cache, Branch Prediction) and aggressive logic scheduling to maintain 20 TPS under heavy load.

## ⚡ Key Optimizations

### 📐 Level 0 Math (SIMD/Cache Friendly)

Standard Minecraft relies on massive 65,536-entry trigonometry tables (~256KB), which often cause CPU cache thrashing.

-   **MetalMC** replaces these with **4KB L1-Cache Resident Tables**.
-   **Result**: Math operations stays in the fastest CPU cache tier, significantly speeding up chunk generation, physics, and explosions.

### 🧠 Aggressive AI Throttling

Entity AI is often the #1 cause of lag. MetalMC creates a smarter schedule for mob brains:

-   **Smart Tick Skipping**: Mobs only scan for new goals (like finding a player) every **3 ticks**.
-   **Result**: **~60% reduction** in Entity Tick MSPT in crowded farms or dungeons, with minimal impact on gameplay feel.

## 🛠️ Usage

MetalMC is a drop-in replacement for Paper.

1.  Download the JAR from [Releases](https://github.com/Just-Haze/MetalMC/releases).
2.  Replace your existing `server.jar`.
3.  Run it!

## 🏗️ Build from Source

Requirements: **JDK 21**

```bash
git clone https://github.com/Just-Haze/MetalMC.git
cd MetalMC
./gradlew applyPatches
./gradlew createMojmapBundlerJar
```

Find your build in `paper-server/build/libs`.

## 🤝 Attribution

MetalMC is built on the shoulders of giants.

-   **PaperMC**: The foundation of this project.
-   **Just-Haze**: "Level 0" Optimization implementation.

---

_Licensed under MIT_
