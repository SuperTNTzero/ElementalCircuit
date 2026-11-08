# 开发与交付

`main` 保存可构建的开发基线。新任务从 `main` 创建 `codex/` 功能分支；历史分支保留，不改写既有提交。

每次完成功能或修复均递增开发版本，同步 Gradle、模组元数据及当前版本说明。运行核心测试；涉及 Minecraft 交互时运行原生 GameTest。记录准确结果和未覆盖范围，通过所需验证后构建安装包、生成 SHA-256、更新发布清单并提交 Git。提交授权不包含自动推送。

当前维护 Minecraft 1.21.1 / NeoForge 21.1.265 / Java 21；`1.20.1/` 是历史工程。构建入口为 `1.21.1/tools/build.ps1`：

```powershell
cd 1.21.1
./tools/build.ps1 -Tasks build,runGameTestServer -Mirror -Offline
```

离线构建需要本地已有 Gradle、NeoForge 和 Minecraft 缓存；首次构建去掉 `-Offline`，并按项目说明准备 `libs/` 下指定版本的 Curios 和 Patchouli。缓存、测试世界、日志、依赖源码包不提交。第三方音画资源的授权及署名随模组保留；项目自身许可见 `1.21.1/LICENSE.txt`。

运行规则放在领域层，Minecraft 世界操作、持久化与渲染通过适配层接入。玩家文本写入中英文语言 JSON。测试使用隔离世界，不读写正式玩家存档。
