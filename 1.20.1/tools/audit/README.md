# Historical state-audit fixtures

Defect-reproduction and short sampling tools for the **0.10.0 / Minecraft 1.20.1** baseline. These are not normal regression tests and are not included in the release JAR. The later `RuntimeBoundaryMinecraftContracts` suite checks the corrected behavior.

`StateAudit` deliberately expects historical defects, including data loss. Its success therefore does not mean that the mod is behaving correctly. Do not inject these probes into newer builds or player worlds.

## Historical execution environment

The probes require JDK 17, the old Gradle setup, and populated dependency caches. Commands run from the `1.20.1/` project directory:

```powershell
./tools/test-core.ps1
New-Item -ItemType Directory -Force build/audit/core
& "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -cp 'build/core-test;build/core-test/gson.jar' -d build/audit/core tools/audit/core/CoreAudit.java
& "$env:JAVA_HOME/bin/java.exe" -Xmx512m -cp 'build/audit/core;build/core-test;build/core-test/gson.jar;src/main/resources' CoreAudit
```

The optional historical GameTest probe uses `tools/audit/audit.init.gradle` with the old Gradle runner and an isolated `build/gametest` world. It intentionally emits an error log while probing oversized `StringTag` behavior. Run it only in a disposable checkout, separate from normal regression compilation, so old probe classes cannot contaminate subsequent runs.

## Sampling scope

The idle fixture uses four slots per wand, 64 disconnected signal runes per slot, and stopped supply. GameTest sampling uses warm-up iterations followed by short timings; the core probe samples JSON, VM, and allocation costs. These tools are not JMH benchmarks and do not measure full server TPS, GPU frame rate, compressed multiplayer traffic, or long-running GC behavior.
