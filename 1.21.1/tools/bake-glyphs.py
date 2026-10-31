"""Bake only the rune atlas, preserving workstation, wand and HUD assets."""
from pathlib import Path
import json
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
dest = ROOT / 'textures/gui/glyphs.png'
image.save(dest)
dest.with_suffix('.png.mcmeta').write_text('{"texture":{"blur":true,"clamp":true}}\n', encoding='utf-8')
print(f'Baked {len(art["shapes"])} glyphs: {image.size}')
