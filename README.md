# MetalMC

<img height="100" src="https://via.placeholder.com/100/000000/FFFFFF/?text=MetalMC" align="right" alt="MetalMC Logo">

![License](https://img.shields.io/github/license/Just-Haze/MetalMC)
![Discord](https://img.shields.io/discord/1234567890?label=discord&color=7289DA)
![Stars](https://img.shields.io/github/stars/Just-Haze/MetalMC?style=social)

**MetalMC** is a drop-in replacement for [Paper](https://github.com/PaperMC/Paper) servers designed for **extreme performance** and **hardware-level optimization**.

MetalMC implements "Level 0" optimizations (SIMD/Native Math, Aggressive Scheduling) to push Minecraft server performance beyond what is possible with standard Java logic.

## ⚡ Features

-   **Level 0 Math**: Custom L1-cache friendly trigonometry tables (4KB) replacing the massive Vanilla LUTs (256KB), reducing cache thrashing.
-   **Aggressive AI Throttling**: Smart Entity Goal Selector scheduling to reduce AI overhead by 66% in crowded environments.
-   **Performance**: Built on top of Paper 1.21.10, inheriting all standard optimizations + our metal-bare improvements.
-   **Compatibility**: Drop-in compatible with all Paper/Spigot plugins.

## 📥 Downloads

Downloads are available on the [Releases](https://github.com/Just-Haze/MetalMC/releases) page.

## 🛠️ Building

To build MetalMC, you need JDK 21 installed.

```bash
git clone https://github.com/Just-Haze/MetalMC.git
cd MetalMC
./gradlew applyPatches
./gradlew createMojmapBundlerJar
```

The final jar will be in `paper-server/build/libs/`.

## 🤝 Contributing

Modifications to MetalMC are done via **patches**.

1. Fork the project.
2. Apply patches: `./gradlew applyPatches`
3. Make changes in `paper-server/src/minecraft`.
4. Create patch: `./gradlew rebuildPatches`
5. Commit and PR!

## ⚖️ License

All original code in MetalMC is licensed under MIT.
Code upstreamed from Paper/Spigot/Minecraft follows their respective licenses.
