# 美术工具

脚本从自身位置定位 `1.21.1/` 工程。请在工程根目录执行下面的命令。Blender 和 Aseprite 路径使用当前开发机安装位置，可按需要调整。

| 类型 | 入口 |
| --- | --- |
| Blender 模型 | `model-*-blender.py`：法杖、书本、工作台、固定法阵、法袍和手册 |
| 像素绘制 | `paint-*.lua`、`draw-*.py`、`paint-guide-atlas.py` |
| 静态资源生成 | `bake-*.py`、`generate-resources.py` |
| 音频 | `bake-audio.py`、`requirements-audio.txt` |
| UV 与校准 | `blender_mesh_atlas.py`、`unwrap-wand-materials.py`、`align-wand-grip.py` |
| 工作台检查 | `check-workstations.py` |
| 已有 Blender MCP 桥接 | `blender-mcp-*.py`；本地连接与临时会话配置 |

手册模型与贴图：

```powershell
python tools/artwork/paint-guide-atlas.py
& 'E:/Aseprite-v1.3.15.5/build/bin/aseprite.exe' -b art/aseprite/guidebook/guide_atlas.png --save-as art/aseprite/guidebook/guide_atlas.aseprite
& 'E:/Aseprite-v1.3.15.5/build/bin/aseprite.exe' -b art/aseprite/guidebook/guide_atlas.aseprite --save-as src/main/resources/assets/elementalcircuits/textures/item/guide_atlas.png
& 'D:/blender/blender-5.2.3-windows-x64/blender.exe' -b --python tools/artwork/model-guide-blender.py
python tools/artwork/bake-guidebook.py
```

修改 Aseprite 原稿后直接导出贴图即可；重新运行绘制脚本会重建原稿 PNG。模型生成器同时保存 Blender 原稿、OBJ、材质引用和显示变换。资源生成器会更新对应资源，请只在有意重建时运行。

`bake-mana-ecology.py` 重建魔力史莱姆、凝胶、浮光光晕和瞬时恢复效果图标，依赖Pillow。
