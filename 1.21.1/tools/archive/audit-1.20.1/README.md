# Historical 1.20.1 audit fixtures

Archived probes for NBT serialization, connection endpoints, and runtime overhead in the old Minecraft 1.20.1 edition. They retain the old Forge APIs and defect-reproduction expectations. They are excluded from the current 1.21.1 source sets and release JAR.

Passing a defect-reproduction probe does not establish correct behavior. Use the current contract suite for regression checks; from the `1.21.1/` project directory:

```powershell
./tools/build.ps1 -Tasks build,runGameTestServer -Mirror -Offline
```

[HISTORICAL.md](HISTORICAL.md) preserves the original execution notes. Its paths, dependencies, and assumptions apply to the historical environment only; do not inject these fixtures into a current game or normal player world.
