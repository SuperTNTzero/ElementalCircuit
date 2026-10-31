"""Original static casting HUD artwork. Pillow only; no runtime texture generation."""
from pathlib import Path
import json
from PIL import Image, ImageDraw

out=Path(__file__).resolve().parents[1]/'src/main/resources/assets/elementalcircuits/textures/gui/hud'
out.mkdir(parents=True,exist_ok=True)
scale=4
def ring(name,outer,inner,color):
    im=Image.new('RGBA',(128*scale,128*scale))
    d=ImageDraw.Draw(im)
    for r,fill in ((outer,color),(inner,(0,0,0,0))):
        d.ellipse(((64-r)*scale,(64-r)*scale,(64+r)*scale,(64+r)*scale),fill=fill)
    im.resize((128,128),Image.Resampling.LANCZOS).save(out/(name+'.png'))
    (out/(name+'.png.mcmeta')).write_text(json.dumps({'texture':{'blur':True,'clamp':True}})+'\n')
ring('cooldown_track',58,38,(15,20,26,82))
ring('cooldown_blue',53,43,(73,174,243,150))

im=Image.new('RGBA',(144,34));d=ImageDraw.Draw(im)
d.rounded_rectangle((0,0,143,33),radius=4,fill='#0d141b',outline='#30281b',width=1)
d.rounded_rectangle((1,1,142,32),radius=3,fill='#182128',outline='#b28b45',width=1)
d.line((4,2,139,2),fill='#eed19b');d.line((3,31,140,31),fill='#715027')
d.rounded_rectangle((4,4,139,29),radius=1,outline='#4a4030',width=1)
for x in (4,139):
    for y in (4,29):d.point((x,y),fill='#e0bb77')
d.rectangle((7,22,136,27),fill='#0b141b',outline='#5a4b31')
im.save(out/'mana_frame.png')
print('HUD artwork baked: 2 antialiased rings, brass mana frame')
