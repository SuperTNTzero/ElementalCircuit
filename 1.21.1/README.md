# Elemental Circuits — Minecraft 1.21.1

Design spells by engraving connected runes on hexagonal circuit plates. Elements, delivery methods, shapes, parameters, and secondary effects are separate building blocks; signal paths control when mana flows and when a spell is released.

**Version:** 0.55.2 · **Mod ID:** `elementalcircuits`

## Requirements and installation

| Component | Version | Requirement |
| --- | --- | --- |
| Minecraft Java Edition | 1.21.1 | Required |
| NeoForge | 21.1.265 | Required; reference loader version |
| Java | 21 | Required |
| Curios API | 9.5.1+1.21.1 | Required on client and server |
| Patchouli | 1.21.1-93-NEOFORGE | Optional guide integration |

Place [the mod JAR](../releases/elemental-circuits-1.21.1-0.55.2.jar) and [Curios](../releases/dependencies/curios-neoforge-9.5.1+1.21.1.jar) in the instance's `mods/` directory. Use the same mod version on both sides of a server connection. Patchouli adds search, bookmarks, and recipe pages to the guide; without it, the same guide item opens a vanilla-style reader.

This edition is a development build. Back up worlds before updating. It reads the current data formats and does not promise migration from earlier development versions or the historical 1.20.1 edition.

## Your first circuit

1. Craft a **Circuit Field Guide** from a book, blue dye, and a gold nugget. It explains equipment, engraving, mana, and troubleshooting in English or Simplified Chinese.
2. Obtain an **Inscribed Wand**, an **Inscription Table**, and rune materials. New wands are blank. To assemble or replace wand components, use the separate **Wand Workbench**, which needs two adjacent spaces.
3. Craft mana dust from redstone dust, glowstone dust, and a mana crystal; the recipe yields eight. At a **Rune Engraving Table**, one paper and one mana dust produce one selected rune item.
4. Use the wand on the Inscription Table. Place and connect a simple chain: **Mana Input → Fire conversion → Directed casting → Beam → Output**. Keep the input within your available mana and save the circuit.
5. Close the editor and right-click to cast. A short click casts once; holding and releasing charges a cast. Circuits configured for continuous operation keep supplying mana until stopped or a termination condition is reached.

The editor changes a draft until you save. Survival engraving charges only positive increases in each rune type; moving a rune, changing its parameters, or saving the same design again does not charge another item. Removing runes does not refund them. Creative mode is exempt. Blueprint imports follow the same rule when installed on a real carrier; exporting or adding a design to a notebook is free.

## Editing and selecting spells

The left sidebar groups runes into expandable categories with shape previews. Select a rune to place it, or use the selection tool to move an existing rune. The top rows select the physical plate slot and the circuit layer; plate management and derived-circuit relationships have separate pages.

| Input | Action |
| --- | --- |
| Route tool, then source and destination | Draw a directed mana or signal connection; nonadjacent runes are supported |
| Empty cells while routing | Add bends to the route |
| Right-click / Esc | Cancel placement or routing |
| Delete in route mode | Remove the highlighted individual connection |
| Delete in selection mode | Remove the selected rune |
| Ctrl+Z / Ctrl+Y | Undo / redo draft edits |
| Enter | Save an open parameter field first; otherwise save the circuit |
| Mouse wheel / middle-button drag on the grid | Zoom / pan |

Routes may cross or share cells without connecting. They cannot pass through an unrelated rune. Transmission loss depends on the actual route, material, and endpoints.

Default gameplay bindings are rebindable in Minecraft's Controls menu:

| Key | Action |
| --- | --- |
| Z / X | Previous / next circuit while holding a wand |
| Hold R | Wand circuit selection wheel |
| B | Book interface |
| Hold V | Equipment circuit selection wheel |
| J | Cast the selected equipment circuit |
| H | Cast an inscribed ring circuit |

A spellbook stores its own circuits. When held offhand beside a wand, its circuits join the wand's selection wheel rather than replacing the wand's own choices. A blueprint notebook stores designs and previews, without copying mana, durability, item identity, or running state.

## Mana, equipment, and fixed circuits

- **Mana and timing:** input is a fixed request; insufficient supply fails the cast rather than silently reducing it. A standard-state model defines costs, circuit complexity contributes to preparation time, and wand components determine the release interval. Dedicated storage runes can accumulate repeated inputs; ordinary runes are not long-term mana batteries.
- **Elements:** fire and water can create real fire and water sources; light creates temporary illumination, darkness suppresses light sources, and arcane mana can pass through blocks. Reactions resolve in arrival order, with opposing elements consumed before remaining effects apply. Vanilla damage immunity intervals are respected.
- **Wands and plates:** the head determines strength and speed, the head and body contribute durability, and the tail determines melee damage. Plate durability is separate from wand durability. Default safe loads are 128 / 512 / 1280 for wood / brass / crystal; overload increases wear, and reaching four times the safe load causes a meltdown. The wand and design remain, but a ruined plate needs replacement at the Inscription Table.
- **Layered circuits:** derived spells use additional circuit layers on the same plate. Choose an explicit hit or timer trigger. Material-based recursion limits and server budgets bound execution.
- **Fixed circuits:** use a plate on the Inscription Table, draw the circuit, and finish engraving to obtain a placeable circuit. The plate is consumed on completion. A Tuning Wand controls operation, slot selection, and charging; mana dust connects fixed circuits. The gold-framed Circuit Monocle fits either a helmet slot or a functional Curios head slot and reveals nearby loaded connections.
- **Mana ecology:** crystal ore, natural nodes, artificial nodes, and floating nodes feed a finite mana field. Environmental intake runes draw from it. A Survey Wand inspects circuits and nodes and surveys natural nodes without forcing chunks to load. Mana fruit increases capacity; potions provide regeneration or instant restoration, depending on the recipe.
- **Equipment and automation:** crystal-studded robes and inscribed rings add attributes and equipment spells. Earth magic combines with container access, output, filtering, and redstone runes for material handling. Contact casting combines with independent force, constraint, and interaction effects.

Recipes are available through the in-game guide; detailed formulas and system limits are linked from the [documentation index](docs/README.md).

## Blueprints and custom effects

The sharing page imports and exports blueprint text codes. Loading a blueprint changes the draft; saving applies it and checks the target plate, server limits, and rune inventory. A notebook can keep multiple named designs with previews.

Custom casting and impact effects use `.ecfx` packages in `config/elementalcircuits/effects/`. Packages contain a JSON manifest, a PNG spritesheet, and optional PCM WAV audio, independent of Minecraft resource packs. Assign them through the custom-effects rune category. Effects are client presentation only: they do not change authoritative damage or world interactions, and other clients need the same local packages to see the same custom assets. See the [package format](docs/systems/自定义特效包.md) and [manifest example](config_examples/effects/ember/manifest.json).

## Server configuration

Administrators can manage mana, recovery, field dynamics, circuit limits, and performance budgets through `/ec` commands and the mod's server settings. Use command completion to inspect available options. For example:

```text
/ec config set inscription.requireRunes 0
/ec config set probe.radius 500
```

`inscription.requireRunes` defaults to `1`; set it to `0` for direct engraving without crafting rune items. See [server settings](docs/systems/服务端配置与符文边界.md), [costs and mana dynamics](docs/systems/施法成本与魔力动力学.md), and [standard state and storage](docs/systems/标准状态与专用储魔.md) for the underlying rules. These reference documents are in Chinese.

## Build from source

Run from this directory with JDK 21:

```powershell
./tools/build.ps1 -Tasks build
./tools/build.ps1 -Tasks build,runGameTestServer
./tools/build.ps1 -Tasks runClient
```

The script uses Gradle 9.2.1 and ModDevGradle 2.0.148. Prepare the Curios and Patchouli JARs specified in `build.gradle` under `libs/`. Use `-JavaHome` to select a JDK, `-Mirror` for the alternate NeoForge endpoint, and `-Offline` only after dependencies are cached. The Gradle wrapper also supports `./gradlew.bat build`.

The output is `build/libs/elemental-circuits-1.21.1-0.55.2.jar`. `build` includes the pure Java core contracts; `runGameTestServer` runs Minecraft integration tests in isolated worlds. Test fixtures and client smoke tools are excluded from the release JAR.

For tool details and optional client checks, see [tools](tools/README.md), [architecture](ARCHITECTURE.md), and [contribution guidelines](../CONTRIBUTING.md).

## License

See [LICENSE.txt](LICENSE.txt) for project rights and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for third-party asset licenses and attribution.
