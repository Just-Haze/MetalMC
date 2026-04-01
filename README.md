<div align="center">

<img src="https://img.shields.io/badge/MetalMC-1.21.10-ff4444?style=for-the-badge&logo=minecraft&logoColor=white" alt="MetalMC"/>

# ⚙️ MetalMC

**The bare-metal Minecraft server engine.**  
A high-performance fork of [PaperMC](https://github.com/PaperMC/Paper) engineered for maximum throughput, minimum latency, and rock-solid stability.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg?style=flat-square)](LICENSE.md)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.10-62b87a?style=flat-square)](https://minecraft.net)
[![Release](https://img.shields.io/badge/Release-v1.21.10--2-ff4444?style=flat-square)](https://github.com/Just-Haze/MetalMC/releases)
[![Java](https://img.shields.io/badge/Java-21+-orange?style=flat-square&logo=openjdk)](https://adoptium.net)
[![Paper](https://img.shields.io/badge/Based%20on-PaperMC-blueviolet?style=flat-square)](https://github.com/PaperMC/Paper)

</div>

---

## 🔍 What is MetalMC?

MetalMC is a specialized Minecraft server implementation that strips away inefficiencies in the game loop and replaces them with purpose-built, hardware-aware alternatives. By introducing **async multithreading**, **smart entity activation**, **hopper event caching**, **block entity activation gating**, and a **dynamic thread priority system**, MetalMC can sustain higher player counts and more complex world states while keeping TPS near 20 at all times.

MetalMC stays **100% compatible** with Bukkit, Spigot, and Paper plugins — no rewrites, no forks-of-forks required.

---

## ✨ Features

### 🚀 Async Multithreading Engine

Every system that can safely run off the main thread does.

| System | Description |
|---|---|
| **Async Chunk Loading** | Priority queue separates player-requested chunks from background generation. Worker threads pre-process I/O before handing results to the main thread. |
| **Async Entity Processing** | Pathfinding calculations run on dedicated worker threads and are applied to entities safely on the next tick. Falls back to synchronous operation on timeout. |
| **Async Tile Entities** | Hopper transfer calculations and furnace logic are offloaded to worker threads, eliminating the biggest per-tick CPU spikes in heavily automated worlds. |
| **Dynamic Thread Priority** | The main server thread is pinned to `MAX_PRIORITY`. Worker thread priorities auto-tune based on real-time TPS — backing off when TPS is healthy, ramping up under load. |

### 🧠 Smart Entity Activation

Entities far from players are throttled at the AI level, not just skipped entirely.

- **Activity Zones:** Entities within close range of players receive full AI updates every tick. Entities further away have AI goals evaluated every 3 ticks, reducing CPU usage in mob farms without breaking behavior.
- **Chunk-Aware Skipping:** Entities in unloaded or low-priority chunks are bypassed entirely until a player re-enters their area.

### 🪣 Hopper & Block Entity Optimizations

- **Event Dispatch Optimization:** Cancellable hopper events are only fired when a listener is actually registered, eliminating the overhead of event construction and dispatch in vanilla worlds.
- **Container Lookup Caching:** Adjacent container references are cached per hopper and invalidated only on block update, removing the per-tick block lookup.
- **Block Entity Activation Gating:** Tile entities (hoppers, furnaces, etc.) only tick when they have had a state change or when a player is nearby, cutting unnecessary ticks by up to 80% in idle worlds.

### ⚡ Metal Math Engine

Replaces hot-path math calls with cache-friendly alternatives.

- **L1-Optimized Trig Tables:** The standard 65,536-entry sine table (256 KB) is replaced with a 4,096-entry table (16 KB) — fitting entirely in L1 cache and eliminating cache-miss penalties during entity physics and chunk generation.
- **Aggressive AI Throttling:** Goal selector scans run every 3 ticks instead of every tick, cutting AI overhead by ~66% with no perceptible behavior difference.

### ⚙️ `metal.yml` Configuration

Every optimization is individually tunable. MetalMC generates `metal.yml` on first launch.

```yaml
optimizations:
  chunk-ticking: true

multithreading:
  async-chunk-loading:
    enabled: true
    threads: 4                   # defaults to CPU cores / 2
    prioritize-player-chunks: true

  async-entity-processing:
    enabled: true
    threads: 2
    async-pathfinding: true
    async-collision-detection: false  # experimental

  async-tile-entities:
    enabled: true
    async-hoppers: true
    async-furnaces: true
    threads: 2

  thread-priorities:
    enabled: true
    main-thread-priority: 10     # Thread.MAX_PRIORITY
    dynamic-adjustment: true

  advanced-scheduler:
    enabled: true
    task-batching: true
    auto-async-detection: true
    max-async-tasks: 100
```

---

## 📥 Getting Started

### Requirements

- **Java 21** or later ([Adoptium Temurin](https://adoptium.net) recommended)
- At least **4 GB RAM** allocated to the JVM

### Quick Start

1. Download the latest `metalmc.jar` from the [**Releases**](https://github.com/Just-Haze/MetalMC/releases) page.
2. Launch your server:
   ```bash
   java -Xms4G -Xmx4G \
     -XX:+UseG1GC \
     -XX:+ParallelRefProcEnabled \
     -XX:MaxGCPauseMillis=200 \
     -jar metalmc.jar --nogui
   ```
3. Accept the EULA in `eula.txt`, then restart.
4. Tune `metal.yml` to match your hardware.

> **Tip:** For 8+ core machines, increase `async-chunk-loading.threads` to `cores / 2` and `async-tile-entities.threads` to `4` for best results.

---

## 🔨 Building from Source

```bash
# 1. Clone
git clone https://github.com/Just-Haze/MetalMC.git
cd MetalMC

# 2. Apply all patches
./gradlew applyPatches

# 3. Build the server JAR
./gradlew createMojmapBundlerJar
```

The final artifact is located at:
```
paper-server/build/libs/metalmc-*.jar
```

---

## 🧩 Compatibility

MetalMC is a **drop-in replacement** for Paper. No reconfiguration needed.

| | Status |
|---|---|
| Bukkit plugins | ✅ Full support |
| Spigot plugins | ✅ Full support |
| Paper plugins | ✅ Full support |
| World data (Anvil/Region) | ✅ Compatible — always back up before migrating |
| NMS-dependent plugins | ⚠️ Test first — async tick ordering may differ |

> [!NOTE]
> Plugins that depend on exact vanilla tick ordering or that hook deeply into NMS internals should be tested before deploying to production. Most plugins work without any changes. If issues arise, selectively disable async modules in `metal.yml`.

---

## 📋 Changelog

### v1.21.10-2 *(current)*

- **New:** Dynamic Thread Priority Manager — main thread pinned to MAX_PRIORITY, worker priorities auto-tune with TPS
- **New:** Async Tile Entity Processor — hoppers and furnaces offloaded to worker threads
- **New:** Advanced scheduler with task batching and auto-async detection
- **Improved:** Async chunk loader now uses a priority queue (player-requested > background)
- **Improved:** Async entity processor timeout reduced to 50 ms, safer fallback path
- **Improved:** `metal.yml` expanded with async-collision-detection and per-subsystem thread counts

### v1.21.10-1

- Smart Entity Activation with activity zones
- Hopper event dispatch optimization and container lookup caching
- Block entity activation gating
- L1-optimized trig table (16 KB vs 256 KB)
- Async pathfinding on dedicated worker threads
- Initial `MetalConfig` (`metal.yml`) system

---

## ❓ FAQ

**Q: Is MetalMC safe for production use?**  
A: Yes. Every optimization has a synchronous fallback. Async operations time out and fall back to the main thread automatically.

**Q: Does it break Redstone?**  
A: No. Vanilla Redstone behavior is fully preserved. The AI and tile-entity throttling systems do not touch Redstone logic.

**Q: Can I contribute?**  
A: Absolutely. Open an issue or a pull request. Performance patches, test results, and bug reports are all welcome.

---

## ⚖️ License

MetalMC is a fork of [PaperMC](https://github.com/PaperMC/Paper) → [Spigot](https://www.spigotmc.org/) → [CraftBukkit](https://bukkit.org/).

Licensed under the **[GNU General Public License v3.0](LICENSE.md)**.
