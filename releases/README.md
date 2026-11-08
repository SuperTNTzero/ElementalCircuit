# Elemental Circuits installation packages

## Current edition

**Elemental Circuits 0.55.2** requires **Minecraft 1.21.1**, **NeoForge 21.1.265**, and **Java 21**.

| Package | Purpose |
| --- | --- |
| [elemental-circuits-1.21.1-0.55.2.jar](elemental-circuits-1.21.1-0.55.2.jar) | Main mod; install on client and server |
| [Curios API 9.5.1+1.21.1](dependencies/curios-neoforge-9.5.1+1.21.1.jar) | Required dependency on both sides |
| Patchouli 1.21.1-93-NEOFORGE | Optional searchable guide and recipe pages |

Put the mod and Curios JARs into the instance's `mods/` directory. Client and server must use the same Elemental Circuits version. Without Patchouli, the guide uses a vanilla-style book reader. See the [player guide](../1.21.1/README.md) for crafting and controls.

These are development packages. Back up worlds before changing versions; older development save and blueprint formats are not guaranteed to load. Do not install different Minecraft editions together in one instance.

## Package verification

[manifest.json](manifest.json) records Minecraft, loader, Java, dependency, file size, and SHA-256 metadata by package. [SHA256SUMS.txt](SHA256SUMS.txt) provides checksums. To inspect a downloaded file in PowerShell:

```powershell
Get-FileHash ./elemental-circuits-1.21.1-0.55.2.jar -Algorithm SHA256
```

Compare the result with the matching manifest entry. The manifest includes historical packages; not every historical binary is included in every checkout.

## Historical edition

The Minecraft **1.20.1** edition ends at mod version **0.14.0**, targeting **NeoForge 47.1.106 / Java 17**. Its features and installation requirements differ from the current edition. See its [own README](../1.20.1/README.md).

## Licenses

Project rights and third-party spell asset attribution are documented in [LICENSE.txt](../1.21.1/LICENSE.txt) and [THIRD_PARTY_NOTICES.md](../1.21.1/THIRD_PARTY_NOTICES.md). Curios provenance and redistribution notices are retained in [dependencies/curios-PROVENANCE.md](dependencies/curios-PROVENANCE.md) and the accompanying license files.
