"""Historical v0.16 mask reference only. Current runtime atlas: bake-spell-assets.py."""
from pathlib import Path
import math
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2] / 'art/vfx/legacy'
CELL, SCALE = 128, 2
size = CELL * SCALE
atlas = Image.new('RGBA', (CELL * 4, CELL * 4), (255, 255, 255, 0))


def polar(radius, angle):
    return (size / 2 + math.cos(angle) * radius * size / 2,
            size / 2 + math.sin(angle) * radius * size / 2)


for kind in range(16):
    mask = Image.new('L', (size, size))
    draw = ImageDraw.Draw(mask)
    if kind in (0, 1, 2, 15):
        for y in range(size):
            for x in range(size):
                u, v = (x + .5) / size * 2 - 1, (y + .5) / size * 2 - 1
                r, a = math.hypot(u, v), math.atan2(v, u)
                if kind == 0:
                    alpha = math.exp(-r * r * 6) * max(0, 1 - r) ** .5
                elif kind == 1:
                    cloud = .80 + .20 * math.sin(a * 5 + r * 9)
                    alpha = max(0, 1 - r / cloud) ** 1.6
                elif kind == 2:
                    alpha = math.exp(-v * v * 22) * min(1, max(0, 1 - abs(u)) * 8)
                else:
                    alpha = max(0, 1 - abs(r - .62 - .08 * math.sin(a * 9)) * 12) * .6
                    alpha += max(0, 1 - r) ** 3
                mask.putpixel((x, y), round(min(1, alpha) * 255))
    elif kind == 3:
        for radius, width, alpha in [(.84, 3, 255), (.72, 1.5, 160)]:
            box = [size * (1-radius)/2]*2 + [size * (1+radius)/2]*2
            draw.ellipse(box, outline=alpha, width=round(width*SCALE))
    elif kind == 4:
        pts = [polar(.92 if i % 2 == 0 else .12, i * math.pi / 4) for i in range(8)]
        draw.polygon(pts, fill=255)
        draw.ellipse([size*.4, size*.4, size*.6, size*.6], fill=255)
    elif kind == 5:
        draw.polygon([(size*.5, size*.04), (size*.62, size*.38), (size*.78, size*.25),
                      (size*.85, size*.66), (size*.63, size*.89), (size*.36, size*.88),
                      (size*.15, size*.64), (size*.27, size*.30), (size*.38, size*.60)], fill=210)
        draw.polygon([(size*.49, size*.29), (size*.66, size*.71), (size*.49, size*.83), (size*.33, size*.69)], fill=255)
    elif kind == 6:
        draw.polygon([polar(.91, -math.pi/2), polar(.46, 0), polar(.71, math.pi/2), polar(.32, math.pi)], fill=210)
        draw.line([polar(.91, -math.pi/2), polar(.71, math.pi/2)], fill=255, width=3*SCALE)
    elif kind in (7, 8):
        for radius in (.85, .73):
            draw.ellipse([size*(1-radius)/2]*2+[size*(1+radius)/2]*2, outline=255, width=2*SCALE)
        for offset in (0, math.pi):
            pts = [polar(.65, i*math.tau/3+offset-math.pi/2) for i in range(3)]
            draw.line(pts + [pts[0]], fill=210, width=2*SCALE)
        for i in range(12):
            a = i*math.tau/12
            draw.line([polar(.76, a), polar(.83, a)], fill=255, width=2*SCALE)
        for i in range(6):
            a = i*math.tau/6
            draw.line([polar(.37, a), polar(.48, a+.10), polar(.56, a)], fill=220, width=2*SCALE)
    elif kind == 9:
        pts = [(.53,.04),(.29,.37),(.50,.42),(.25,.77),(.47,.63),(.42,.96),(.73,.49),(.55,.51),(.76,.19)]
        draw.polygon([(x*size,y*size) for x,y in pts], fill=255)
    elif kind == 10:
        pts = [(.20,.29),(.48,.08),(.81,.25),(.88,.65),(.60,.89),(.28,.74)]
        draw.polygon([(x*size,y*size) for x,y in pts], fill=210)
        draw.line([(size*.20,size*.29),(size*.55,size*.44),(size*.81,size*.25)], fill=255,width=3*SCALE)
        draw.line([(size*.55,size*.44),(size*.60,size*.89)], fill=120,width=3*SCALE)
    elif kind == 11:
        for i in range(3):
            pts = [polar(.30+.48*j/70, i*math.tau/3+j/70*math.pi*1.25) for j in range(71)]
            draw.line(pts,fill=255,width=3*SCALE)
    elif kind == 12:
        pts = [polar(.85 if i%2==0 else .16, i*math.pi/4) for i in range(8)]
        draw.polygon(pts,fill=255)
    elif kind == 13:
        for y in (.31,.5,.69):
            draw.arc([size*.10,size*(y-.19),size*.90,size*(y+.19)],10,170,fill=235,width=3*SCALE)
    else:
        for i in range(9):
            a = i*math.tau/9
            draw.line([polar(.08,a),polar(.38,a+.12),polar(.54,a-.06),polar(.88,a+.1)],fill=230,width=2*SCALE)
    # Soft edge around a crisp center. Entire atlas is a tintable mask, with transparent gutters.
    soft = mask.filter(ImageFilter.GaussianBlur(SCALE * .8))
    from PIL import ImageChops
    mask = ImageChops.lighter(mask, soft).resize((CELL,CELL),Image.Resampling.LANCZOS)
    sprite = Image.new('RGBA',(CELL,CELL),(255,255,255,0));sprite.putalpha(mask)
    atlas.paste(sprite,((kind%4)*CELL,(kind//4)*CELL))

dest = ROOT / 'spells.png'
dest.parent.mkdir(parents=True,exist_ok=True)
atlas.save(dest)
dest.with_suffix('.png.mcmeta').write_text('{"texture":{"blur":true,"clamp":true}}\n',encoding='utf-8')
print('BAKED_EFFECT_ATLAS: 16 original masks; 512x512 RGBA')
