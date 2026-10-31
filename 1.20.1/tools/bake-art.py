"""Bake checked-in vector artwork to shared atlases. Offline authoring only; never runs in-game."""
from pathlib import Path
import json
import math
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/elementalcircuits'
art = json.loads((ROOT / 'art/glyphs.json').read_text(encoding='utf-8'))
cell, columns = art['cell'], art['columns']
image = Image.new('RGBA', (art['atlasWidth'], art['atlasHeight']))
draw = ImageDraw.Draw(image)
for index, strokes in enumerate(art['shapes'].values()):
    cx, cy = (index % columns + .5) * cell, (index // columns + .5) * cell
    scale = cell / 2 - art['padding']
    for x1, y1, x2, y2 in strokes:
        draw.line((cx+x1*scale, cy+y1*scale, cx+x2*scale, cy+y2*scale), fill='white', width=art['strokeWidth'])
dest = ROOT / 'textures/gui'
dest.mkdir(parents=True, exist_ok=True)
image.save(dest / 'glyphs.png')

# White masks share one material; per-element color and opacity are instance data.
cell = 64
image = Image.new('RGBA', (cell*4, cell))
for kind in range(4):
    for y in range(cell):
        for x in range(cell):
            u, v = (x+.5)/32-1, (y+.5)/32-1
            r = math.hypot(u, v)
            if kind == 0: alpha = max(0, 1-r)**1.6
            elif kind == 1: alpha = max(0, 1-r*r)**2 * .6
            elif kind == 2: alpha = max(0, 1-abs(v))**2 * min(1, (1-abs(u))*8)
            else: alpha = max(0, 1-abs(r-.83)*30) + max(0, 1-abs(r-.68)*40)*.3
            image.putpixel((kind*cell+x,y), (255,255,255,round(min(1,alpha)*255)))
dest = ROOT / 'textures/effect'
dest.mkdir(parents=True, exist_ok=True)
image.save(dest / 'spells.png')
for path in [ROOT/'textures/gui/glyphs.png', dest/'spells.png']:
    path.with_suffix('.png.mcmeta').write_text('{"texture":{"blur":true,"clamp":true}}\n', encoding='utf-8')
print(f'Baked {len(art["shapes"])} glyphs and 4 effect sprites')

# The UI uses the same 27 component combinations as the existing inventory models.
image = Image.new('RGBA', (128, 64))
for index in range(27):
    sprite = Image.open(ROOT / f'textures/item/circuit_wand_{index}.png').convert('RGBA')
    image.paste(sprite, ((index % 8)*16, (index // 8)*16))
image.save(ROOT / 'textures/gui/wands.png')
