# 法阵手札封面

原创16×16像素贴图，深蓝书皮、黄铜包角、小块水晶与浅色书页，采用原版书的斜置轮廓。

- `circuit_guide.aseprite`：Aseprite可编辑源文件。
- `circuit_guide.png`：原稿。
- `cover-preview.png`：最近邻放大的审查图，不随模组发布。
- 运行时贴图：`src/main/resources/assets/elementalcircuits/textures/item/circuit_guide.png`。

`tools/bake-guidebook.py` 生成像素原稿。Aseprite 批处理负责源文件存储与最终贴图导出。阅读器使用 Minecraft / Patchouli 自带纸页纹理作为独立运行时依赖，没有复制第三方 GUI 贴图到本项目。
