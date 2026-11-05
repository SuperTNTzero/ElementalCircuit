"""Original pixel field notes and navy/brass materials, padded for native OBJ mipmaps."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
image = Image.new('RGBA', (256, 128))
palette = ['#1b2b45', '#cdc0a0', '#eee3c5', '#eee3c5', '#ba9655', '#3684a2', '#eee3c5', '#eee3c5']
for tile, color in enumerate(palette):
    patch = Image.new('RGBA', (48, 48), color)
    draw = ImageDraw.Draw(patch)
    if tile == 1:
        for y in range(3, 48, 4):
            draw.line((0, y, 47, y), fill='#a99c7f')
    elif tile in (2, 3):
        ink = '#446778'
        draw.line((4, 3, 43, 3), fill='#a78348')
        if tile == 2:
            for y in (8, 13, 18, 23):
                for x in range(5, 40, 7):
                    draw.line((x, y, x + 4, y), fill=ink)
            for x in (10, 24, 38):
                draw.rectangle((x - 3, 31, x + 3, 37), outline=ink)
            draw.line((13, 34, 21, 34), fill='#b28d4d')
            draw.line((27, 34, 35, 34), fill='#b28d4d')
        else:
            draw.polygon([(24, 9), (37, 16), (37, 31), (24, 38), (11, 31), (11, 16)], outline=ink)
            draw.line((11, 23, 37, 23), fill='#b28d4d')
            draw.line((24, 9, 24, 38), fill='#b28d4d')
            draw.rectangle((22, 21, 26, 25), fill='#3684a2')
    elif tile == 0:
        draw.rectangle((2, 2, 45, 45), outline='#8f7447')
        draw.rectangle((5, 5, 42, 42), outline='#304663')
    x0, y0 = (tile % 4) * 64, (tile // 4) * 64
    for y in range(64):
        for x in range(64):
            image.putpixel((x0 + x, y0 + y), patch.getpixel((max(0, min(47, x - 8)), max(0, min(47, y - 8)))))
source = ROOT / 'art/aseprite/guidebook/guide_atlas.png'
source.parent.mkdir(parents=True, exist_ok=True)
image.save(source)
image.save(ROOT / 'src/main/resources/assets/elementalcircuits/textures/item/guide_atlas.png')
print('GUIDE_ATLAS_PASS: 256x128 original padded pixel materials')
