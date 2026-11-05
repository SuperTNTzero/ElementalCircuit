"""Re-export authored Aseprite tiles and build the particle atlas. Never flatten painted materials."""
from pathlib import Path
import os
import subprocess
from PIL import Image
root = Path(__file__).resolve().parents[2]
folder = root / 'src/main/resources/assets/elementalcircuits/textures/item/wand'
aseprite = Path(os.environ.get('ASEPRITE', 'E:/Aseprite-v1.3.15.5/build/bin/aseprite.exe'))
for i in range(16):
    source = root / f'art/aseprite/parts/palette_{i}.aseprite'
    if not source.is_file():
        raise FileNotFoundError(f'Missing authored material: {source}')
    subprocess.run([str(aseprite), '--batch', str(source), '--save-as', str(folder / f'palette_{i}.png')], check=True)
atlas = Image.new('RGBA', (128, 128))
for i in range(16):
    with Image.open(folder / f'palette_{i}.png') as tile:
        atlas.paste(tile.convert('RGBA').resize((32,32), Image.Resampling.NEAREST), (i % 4 * 32, i // 4 * 32))
atlas.save(folder.parent / 'wand_palette.png')
print('Exported 16 Aseprite materials and static particle atlas')
