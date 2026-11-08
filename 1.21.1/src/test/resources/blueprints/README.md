# Blueprint fixtures

[index.tsv](index.tsv) is the ordered mapping of blueprint files to notebook page names. Each `.ecblueprint` file contains one complete EC1 text code. Copy the whole file into the in-game blueprint import page to load its design.

The fixtures cover beams, fireballs, shields, fans, walls, rings, columns, crosses, domes, alternating elements, homing / bouncing, splitting / chaining, environmental intake, and stored-mana casting. Designs use crystal plates and the dedicated-storage rules. They contain configuration only: no mana reserves, item identities, inventory, or runtime snapshots.

Loading a fixture still respects the destination plate's material and size, available mana, environmental conditions, server budgets, and rune-item requirements. Saving to a real carrier applies the same material checks as a hand-drawn circuit.

## Use an isolated test notebook

Run from the `1.21.1/` project directory with the build dependencies cached:

```powershell
./tools/test-blueprints.ps1 -Mirror -Offline -Interactive
```

The client creates an isolated world under `build/blueprint-smoke/` with a notebook populated from the index. Omit `-Interactive` to check page selection, previews, and copying automatically, then exit. The fixtures and injector are excluded from the release JAR; normal worlds do not receive these designs automatically.

To edit a fixture, replace its text code with a valid export and keep the index in sync. Example circuits belong in blueprint data rather than dedicated editor buttons.
