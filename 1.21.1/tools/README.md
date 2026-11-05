# 开发工具入口

| 入口 | 用途 |
| --- | --- |
| `build.ps1` | JDK21构建、核心与原生测试、隔离客户端验证 |
| `test-core.ps1` | 不启动Minecraft的核心契约测试 |
| `test-blueprints.ps1` | 测试蓝图注入蓝图簿及交互验证 |
| `format-java.ps1` | 四空格Java排版；`-Check`只检查 |
| `run-d-flip-flop.ps1`、`plot-d-flip-flop.py` | D触发器实验与图表 |
| `prepare-promo.py` | 整理已有宣传截图 |
| [artwork](artwork/README.md) | Blender、Aseprite、贴图、音效与资源导出 |
| [archive/audit-1.20.1](archive/audit-1.20.1/README.md) | 旧Forge审计夹具，仅作历史记录 |

所有日常命令均在 `1.21.1/` 工程根目录执行。生成结果、日志和测试世界写入 `build/`，原稿写入 `art/`，运行资源写入 `src/main/resources/`。

`build.ps1 -EcologySmoke -Tasks runClient` 验证魔力史莱姆与浮光节点，使用独立的 `build/ecology-smoke` 世界。
