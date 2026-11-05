"""Bake checked-in vector artwork to shared atlases. Offline authoring only; never runs in-game."""
from pathlib import Path
import json
import math
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/elementalcircuits'
import runpy
runpy.run_path(str(Path(__file__).with_name('bake-workshop.py')))
# Rune artwork has one canonical SVG source and one Inkscape export pipeline.
# No raster draw.line fallback may overwrite the exported atlas.
runpy.run_path(str(Path(__file__).with_name('bake-glyphs.py')), run_name='__main__')
runpy.run_path(str(Path(__file__).with_name('bake-effects.py')))
dest = ROOT / 'textures/gui'

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
