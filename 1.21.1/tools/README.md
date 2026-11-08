# Build and development tools

Run commands from the `1.21.1/` project directory. Building requires JDK 21; artwork tools have separate dependencies and are not required for a normal build.

## Build and verification

```powershell
./tools/build.ps1 -Tasks build
./tools/build.ps1 -Tasks build,runGameTestServer
./tools/build.ps1 -Tasks runClient
python tools/maintenance/check-texts.py
```

`build.ps1` selects Java 21 and the shared Gradle installation/cache under `../tooling/`, then restores the calling process environment. Use `-JavaHome` to select a JDK explicitly, `-Mirror` for the alternate NeoForge endpoint, and `-Offline` only with populated dependency caches. Required library versions are declared in `build.gradle`.

`build` includes the core contracts. `runGameTestServer` runs integration tests in fresh directories under `build/gametest-runs/`. Client smoke modes use separate profiles under `build/`; they do not open normal player worlds. Test classes and smoke tools are not packaged in the mod JAR.

## Tool index

| Entry | Purpose |
| --- | --- |
| `test-core.ps1` | Run pure Java core contracts without starting Minecraft |
| `test-blueprints.ps1` | Populate a blueprint notebook and validate its interface in an isolated client |
| `format-java.ps1` | Format Java with four-space indentation; `-Check` checks without rewriting |
| `maintenance/check-texts.py` | Check English / Chinese localization keys and source references |
| `run-d-flip-flop.ps1`, `plot-d-flip-flop.py` | Run and plot the D flip-flop circuit experiment |
| `prepare-promo.py` | Prepare promotional images from existing screenshots |
| [artwork](artwork/README.md) | Model, texture, glyph, and audio export scripts |
| [archive/audit-1.20.1](archive/audit-1.20.1/README.md) | Historical defect-reproduction fixtures; excluded from current sources |

## Isolated client examples

```powershell
./tools/build.ps1 -Tasks runClient -EditorSmoke -Mirror -Offline
./tools/build.ps1 -Tasks runClient -RelationSmoke -Mirror -Offline
./tools/build.ps1 -Tasks runClient -EcologySmoke -Mirror -Offline
./tools/test-blueprints.ps1 -Mirror -Offline -Interactive
```

Omit `-Interactive` from the blueprint command to run automatic checks and exit. The test notebook is filled from [blueprint fixtures](../src/test/resources/blueprints/README.md); normal gameplay does not receive these designs automatically.

Logs, generated checks, and test worlds belong under `build/`. Editable artwork belongs under `art/`; game-ready exports belong under `src/main/resources/`. See [CONTRIBUTING.md](../../CONTRIBUTING.md) for source and delivery conventions.
