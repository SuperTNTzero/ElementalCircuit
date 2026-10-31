"""Rebuild native OBJ palette textures from the Blender export manifest; Pillow, no Blender needed."""
from pathlib import Path
import json
from PIL import Image

root = Path(__file__).resolve().parents[1]
meta = json.loads((root / 'art/blender/mesh-metrics.json').read_text(encoding='utf-8'))
folder = root / 'src/main/resources/assets/elementalcircuits/textures/item/wand'
folder.mkdir(parents=True, exist_ok=True)
atlas = Image.new('RGB', (16, 16))
for i, color in enumerate(meta['palette']):
    Image.new('RGB', (16, 16), '#' + color).save(folder / f'palette_{i}.png')
    atlas.paste(tuple(bytes.fromhex(color)), (i % 4 * 4, i // 4 * 4, i % 4 * 4 + 4, i // 4 * 4 + 4))
atlas.save(folder.parent / 'wand_palette.png')
print('Baked 16 native wand palette textures')
