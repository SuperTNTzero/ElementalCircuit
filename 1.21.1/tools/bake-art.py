"""Bake checked-in vector artwork to shared atlases. Offline authoring only; never runs in-game."""
from pathlib import Path
import json
import math
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/elementalcircuits'
import runpy
runpy.run_path(str(Path(__file__).with_name('bake-workshop.py')))
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

# Spell material has its own original 16-cell art generator.
runpy.run_path(str(Path(__file__).with_name('bake-effects.py')))
(ROOT/'textures/gui/glyphs.png').with_suffix('.png.mcmeta').write_text('{"texture":{"blur":true,"clamp":true}}\n', encoding='utf-8')
print(f'Baked {len(art["shapes"])} glyphs')

# The UI uses the same 27 component combinations as the existing inventory models.
image = Image.new('RGBA', (256, 128))
for index in range(27):
    sprite = Image.open(ROOT / f'textures/item/circuit_wand_{index}.png').convert('RGBA')
    image.paste(sprite, ((index % 8)*32, (index // 8)*32))
image.save(ROOT / 'textures/gui/wands.png')

# Native 3D wand geometry is exported by Blender, never rebuilt by the UI baker.
runpy.run_path(str(Path(__file__).with_name("bake-wand-palette.py")))

runpy.run_path(str(Path(__file__).with_name("bake-hud.py")))

runpy.run_path(str(Path(__file__).with_name("bake-mana-field.py")))
