# v0.10.0 状态审查实验

**历史复现工具，仅用于 v0.10.0 基线。v0.11 已把相应正确行为写入常规 `RuntimeBoundaryMinecraftContracts`；不要在新版本注入这些期待缺陷发生的实验。**

这些是问题复现和短时采样工具，不属于常规回归，也不进入发布JAR。`StateAudit` 的断言确认当前缺陷仍可复现；它们通过不代表产品正确。修复后应改写为正常行为回归，不能保留“数据必须丢失”的期待。

在模组目录执行，使用已有JDK17、Gradle和本地缓存：

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-17'
./tools/test-core.ps1
& "$env:JAVA_HOME/bin/javac.exe" -encoding UTF-8 -cp 'build/core-test;build/core-test/gson.jar' -d build/audit/core tools/audit/core/CoreAudit.java
& "$env:JAVA_HOME/bin/java.exe" -Xmx512m -cp 'build/audit/core;build/core-test;build/core-test/gson.jar;src/main/resources' CoreAudit

& './build/tooling/gradle-8.1.1/bin/gradle.bat' --gradle-user-home .gradle-home -PuseDependencyMirror=true --offline --no-daemon --console=plain --init-script tools/audit/audit.init.gradle runGameTestServer
```

第一次运行前创建 `build/audit/core`。GameTest使用既有隔离测试世界 `build/gametest`，不访问正式游戏存档。它会生成用于确认StringTag超长行为的预期ERROR日志，并和已有59项正常回归一起运行。实验结束后删除 `build/classes/java/test/dev/elementalcircuits/integration/StateAudit.class`，防止随后直接运行旧编译输出时误带审查实验；保留源文件、日志和报告。

原始记录：`build/audit/core-final.log`、`server-final.log`。第一轮对照为 `core-results.log`、`server-results.log`。最终审查时已移除临时编译类。

采样不是JMH或完整TPS/GPU基准。空转场景为每杖4槽、每槽64个无连线信号符文，所有槽位停供，无世界施法。GameTest每档10次预热、40次计时，统计模块入口耗时；核心探针另测JSON、VM和内存分配。真实联机压缩带宽、存盘峰值和长时GC需后续专项验收。
