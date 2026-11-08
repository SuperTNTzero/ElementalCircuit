# Elemental Circuits — Minecraft 1.20.1

This is the historical **0.14.0** edition of Elemental Circuits, a mod for engraving programmable spells on hexagonal circuit plates. It targets **Minecraft Java 1.20.1**, **NeoForge 47.1.106**, and **Java 17**. The mod ID is `elementalcircuits`.

For the current edition, use the separate [Minecraft 1.21.1 project](../1.21.1/README.md). Features, controls, and data formats described there should not be assumed to exist in this edition.

## Features

- Directed mana and signal paths, elemental conversion, logic gates, counters, clocks, and latches.
- Separate casting methods, sphere / beam / shield shapes, and projectile effects such as splitting, chaining, homing, and bouncing.
- Elemental world interactions: fire, water sources, temporary light, suppressed light sources, and arcane block penetration.
- Three-part wands, multiple circuit slots, fixed circuits, blueprint notebooks, and spellbooks with their own saved circuits.
- A finite player mana pool, persistent circuit state, static spell visuals, and spatial casting sounds.

This edition does not include the current rune-item economy, mana-node ecology, robes, rings, or custom `.ecfx` import system.

## Install

Build the mod or obtain `elemental-circuits-1.20.1-0.14.0.jar` from an available installation archive. Put it in the `mods/` directory of a NeoForge 47.1.106 instance. Both client and server need the mod; no additional gameplay mod is required by this edition.

NeoForge for this Minecraft version still uses `net.minecraftforge` API packages and the `forge` identifier in `mods.toml`. The build targets `net.neoforged:forge:1.20.1-47.1.106`.

## Engraving and casting

Use an Inscribed Wand on an Inscription Table to open the circuit editor. Choose runes in the left sidebar, place them on empty hexes, and connect mana or signal paths with the route tool. Connections can span nonadjacent runes; crossing routes do not automatically connect. Save the draft before casting.

A simple spell connects mana input, elemental conversion, directed delivery, a beam shape, and output. Right-click with the wand to start the saved circuit; use it again to stop supply. A new wand contains no free spell. Wand component changes use the separate Wand Workbench.

The default player mana capacity is 64. Recovery begins 40 server ticks after spending and restores 4 mana per second. This edition injects 4 mana per input, with continuous supply attempts every 10 ticks. These historical input rules differ from the configurable input system in the current edition.

A blueprint notebook stores shareable designs and previews. A spellbook stores runnable circuits; in this edition, holding it offhand beside a wand selects the book's circuits for casting. Z / X change the selection and B opens the book interface. Bindings can be changed in Controls.

## Build and test

Set `JAVA_HOME` to JDK 17 and run from this directory:

```powershell
./gradlew.bat --gradle-user-home .gradle-home build
./gradlew.bat --gradle-user-home .gradle-home runClient
./gradlew.bat --gradle-user-home .gradle-home runGameTestServer
```

The Gradle 8.1.1 wrapper verifies the official distribution checksum. The mod output is `build/libs/elemental-circuits-1.20.1-0.14.0.jar`. The optional `-PuseDependencyMirror=true` switch selects the configured NeoForge dependency mirror.

For core-only checks, use `./tools/test-core.ps1 -JavaHome '<JDK 17 directory>'` with the required Gson dependency available. Historical defect-reproduction tools under [tools/audit](tools/audit/README.md) are separate from the normal test suite.

## Reference and limits

See [ARCHITECTURE.md](ARCHITECTURE.md) for state ownership and adapter boundaries. System references include [workstations and books](docs/工作台与书籍.md), [elemental world interactions](docs/元素世界交互.md), [light, darkness, arcane, and blueprints](docs/光暗奥术与蓝图子界面.md), and [timing runes](docs/计数器与时序符文.md). These documents are in Chinese.

Simulation does not catch up for unloaded chunks or real time spent offline. Shields intercept this mod's spherical spell projectiles, not vanilla arrows, melee attacks, or all entities. Keep separate world backups when moving between Minecraft editions; blueprint import is not a Minecraft world-upgrade mechanism.

See [LICENSE.txt](LICENSE.txt) for project rights.
