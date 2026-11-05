"""Original pixel assets for the mana ecology. Requires Pillow; no downloaded art."""
from pathlib import Path
from PIL import Image, ImageDraw
import math
ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/elementalcircuits/textures"
def save(image, name):
    path = ROOT / name
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
im = Image.new("RGBA", (64, 32)); p = ImageDraw.Draw(im)
p.rectangle((0,0,31,15),fill="#4fa5bfaa")
p.rectangle((8,8,15,15),fill="#54b4cca0")
p.line((9,9,13,9),fill="#baf0f7cd")
p.point((10,10),fill="#baf0f7cd"); p.point((19,12),fill="#8ce6e4b0")
p.rectangle((0,16,23,31),fill="#36788d")
p.rectangle((6,22,11,27),fill="#559eac")
p.rectangle((8,23,9,26),fill="#b9ecdc")
p.rectangle((7,24,10,25),fill="#d4f4e6")
for y in [0,4,8]:
    p.rectangle((32,y,39,y+3),fill="#153349")
    p.rectangle((34,y+1,35,y+2),fill="#2a5970")
save(im,"entity/mana_slime.png")
im = Image.new("RGBA",(16,16)); p=ImageDraw.Draw(im)
p.polygon([(4,3),(10,3),(13,6),(14,11),(11,14),(3,14),(1,11),(2,6)],fill="#19384c")
p.polygon([(4,4),(10,4),(12,7),(13,11),(10,13),(3,13),(2,10),(3,7)],fill="#368bac")
p.polygon([(4,5),(9,5),(11,7),(10,9),(4,9),(3,8)],fill="#65c9d9")
p.line((5,5,8,5),fill="#cfefef")
p.rectangle((7,10,9,11),fill="#80d4ce")
save(im,"item/mana_gel.png")
im=Image.new("RGBA",(64,64)); pix=im.load()
for y in range(64):
    for x in range(64):
        radius=math.hypot(x-31.5,y-31.5)/30
        alpha=int(100*max(0,1-radius)**1.8)
        if radius<.28: alpha=255
        if abs(radius-.55)<.027 or (abs(x-31.5)<1 and radius<.8) or (abs(y-31.5)<1 and radius<.8): alpha=max(alpha,130)
        pix[x,y]=(int(100+150*max(0,1-radius*2)),int(188+65*max(0,1-radius*2)),255,alpha)
save(im,"effect/aerial_node.png")
save(im.resize((16,16),Image.Resampling.NEAREST),"block/aerial_node_core.png")
im=Image.new("RGBA",(18,18)); p=ImageDraw.Draw(im)
p.polygon([(9,1),(14,7),(12,13),(9,16),(5,13),(3,7)],fill="#59c8e0")
p.polygon([(9,4),(11,8),(9,12),(7,8)],fill="#d8f6e9")
save(im,"mob_effect/mana_restoration.png")
