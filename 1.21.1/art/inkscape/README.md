# 符文矢量源稿

`runes.svg` 是符文、元素和工具图标的唯一美术源文件，共81个命名图层，含联系、施力、约束、交互及四个空间参数。用 Inkscape 打开后，在“图层与对象”面板中选择对应图层编辑。圆形使用真实椭圆，火、水、风等元素使用曲线；没有嵌入位图。

保留图层的 `id`、`data-ec-key`、`data-ec-index` 和外层布局变换。图层内部采用 −1 到 1 的标准坐标，可编辑路径和内部变换；保持白色描边、透明背景、圆角连接。不要添加滤镜、文本、外部引用或位图。图集顺序由索引决定，新增符文还须同步Java定义与契约。

本机导出命令，在1.21.1工程根目录运行：

```powershell
& 'D:/Anaconda/python.exe' tools/artwork/bake-glyphs.py --inkscape 'C:/Program Files/WindowsApps/25415Inkscape.Inkscape_1.4.40.0_x64__9waqn51p1ttv2/VFS/ProgramFilesX64/Inkscape/bin/inkscape.exe'
```

导出依赖 Python、Pillow 12.0.0、fontTools 4.60.1 和本地 Inkscape；也可通过 `INKSCAPE_BINARY` 指定可执行文件。当前验证版本为 Inkscape 1.4.4。Windows 商店安装使用 `.exe`，本机同目录 `.com` 无法启动。

脚本调用 Inkscape 导出1024×1408透明图集和24/48/72像素三档指针，不用 Python 逐像素画符文。它检查所有单元的边界、透明度与抗锯齿，全部导出成功后更新 `src/main/resources/assets/elementalcircuits/` 下的PNG、mcmeta与 `art/glyphs.json`。JSON记录源稿SHA-256和供几何契约使用的离散轮廓；这些轮廓不参与绘制。请勿直接编辑生成文件。

游戏颜色由客户端主题和元素决定。普通Gradle构建使用已导出的资源，不要求安装Inkscape；美术修改后须先运行导出命令，再构建模组。
