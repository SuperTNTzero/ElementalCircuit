# 魔力矿物与节点像素源文件

13张16×16贴图，使用有限色阶、整像素色块和原生方块UV；不含外部素材或Minecraft贴图副本。保留深蓝水晶和黄铜的模组配色，降低旧32×32纹理的噪点和细描边。

`tools/draw-mana-blocks.py` 保存可复现的像素形状和原生JSON模型；`tools/export-mana-blocks.ps1` 通过本机Aseprite保存`.aseprite`源文件并导出实际游戏PNG。全部几何由Minecraft原生方块模型烘焙，运行时不生成纹理、不调用Blender或Aseprite。

天然节点为完整岩块内的结晶；人工节点使用黄铜底座、护角和竖直的分面水晶。魔力水晶块为整方块，保留原版建材的完整碰撞与遮挡。
