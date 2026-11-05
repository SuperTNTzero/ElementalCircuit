"""Original pixel artwork for the inscription tools. Offline, deterministic, no data generation."""
from pathlib import Path
import json
import math
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2] / 'src/main/resources/assets/elementalcircuits'
GUI = ROOT / 'textures/gui'
GUI.mkdir(parents=True, exist_ok=True)

# 64px nine-slice tiles: the central field stays quiet, detail belongs to edges.
styles = [
    ('#151a1b', '#202728', '#242b2a'),  # background
    ('#202728', '#575247', '#73634a'),  # recessed panel
    ('#181f21', '#625b49', '#95805a'),  # engraving surface
    ('#29302e', '#524d40', '#796746'),  # button
    ('#343b34', '#9f8758', '#d3b47a'),  # hover
    ('#3f3a2a', '#c6a263', '#efce8e'),  # selected
    ('#232b2b', '#605741', '#a68c5b'),  # category
    ('#1d2425', '#3a403d', '#4f5146'),  # disabled
    ('#25292b', '#8c7455', '#c0a272'),  # leather book
]
atlas = Image.new('RGBA', (256, 256))
for i, (base, edge, light) in enumerate(styles):
    tile = Image.new('RGBA', (64, 64), base)
    d = ImageDraw.Draw(tile)
    d.rectangle((0, 0, 63, 63), outline='#101617')
    d.line((2, 61, 2, 2, 61, 2), fill=edge)
    d.line((3, 62, 62, 62, 62, 3), fill='#101617')
    if i not in (0, 7):
        for x, y in ((3, 3), (60, 3), (3, 60), (60, 60)):
            d.point((x, y), fill=light)
        d.line((5, 3, 10, 3), fill=light)
        d.line((53, 60, 58, 60), fill=edge)
    atlas.paste(tile, ((i % 4) * 64, (i // 4) * 64))
atlas.save(GUI / 'workshop.png')

# One quiet engraving underneath the live hex grid, never an animated overlay.
seal = Image.new('RGBA', (512, 512))
d = ImageDraw.Draw(seal)
for radius, alpha in ((237, 37), (229, 20), (209, 16)):
    d.ellipse((256-radius, 256-radius, 256+radius, 256+radius), outline=(195, 160, 100, alpha), width=1)
for i in range(60):
    a = i * math.tau / 60
    r0 = 218 if i % 5 == 0 else 225
    d.line((256+math.cos(a)*r0, 256+math.sin(a)*r0, 256+math.cos(a)*232, 256+math.sin(a)*232), fill=(195, 160, 100, 42 if i % 5 == 0 else 24))
seal.save(GUI / 'engraving.png')

ITEM = ROOT / 'textures/item'
def wand(head, body, tail):
    im = Image.new('RGBA', (32, 32)); d = ImageDraw.Draw(im)
    # Draw upright, then rotate a pixel-perfect 45 degrees into the inventory silhouette.
    rod = Image.new('RGBA', (16, 38)); p = ImageDraw.Draw(rod)
    colors = ('#a77c4c', '#a3b3b4', '#e8a447')
    p.rectangle((6, 12, 9, 31), fill='#211f1d')
    p.rectangle((7, 13, 8, 30), fill=colors[body]); p.line((7, 14, 7, 29), fill=('#d2ab73', '#d6dedd', '#ffe59a')[body])
    for y in (17, 24, 28):
        p.rectangle((5, y, 10, y+1), fill='#68482e' if body == 0 else '#586469' if body == 1 else '#aa5429')
        p.point((6, y), fill='#c6a464')
    if head == 0:
        p.polygon(((7, 0), (11, 7), (9, 12), (5, 12), (3, 7)), fill='#5c8993', outline='#d8bb7c')
        p.polygon(((7, 2), (9, 7), (7, 11), (5, 7)), fill='#e4f1df'); p.line((7, 3, 7, 8), fill='#ffffff')
    elif head == 1:
        p.polygon(((7, 0), (11, 5), (10, 10), (7, 13), (3, 10), (3, 5)), fill='#523c73', outline='#d5b97d')
        p.polygon(((7, 2), (9, 6), (7, 11), (5, 6)), fill='#b89ce5'); p.line((7, 2, 7, 6), fill='#eee0ff')
    else:
        p.line((3, 3, 3, 9, 7, 12, 11, 9, 11, 3), fill='#ca8352', width=2)
        p.polygon(((7, 3), (9, 7), (7, 10), (5, 7)), fill='#81c9bb'); p.point((7, 5), fill='#d9f5dc')
    p.rectangle((5, 12, 10, 14), fill='#b3985e'); p.line((5, 12, 9, 12), fill='#e4c88b')
    p.rectangle((5, 30, 10, 32), fill='#b89c63')
    if tail == 0: p.rectangle((6, 33, 9, 35), fill='#835e3c'); p.line((6, 33, 9, 33), fill='#caa778')
    elif tail == 1: p.polygon(((5, 33), (10, 33), (9, 37), (6, 37)), fill='#88979e'); p.line((6, 34, 8, 34), fill='#dae3df')
    else: p.polygon(((7, 32), (11, 35), (7, 37), (3, 35)), fill='#65acb1', outline='#b6e8db'); p.point((7, 34), fill='#eeffeb')
    rotated = rod.rotate(-45, resample=Image.Resampling.NEAREST, expand=True)
    box = rotated.getbbox(); rotated = rotated.crop(box)
    im.alpha_composite(rotated, ((32-rotated.width)//2, (32-rotated.height)//2))
    return im

for h in range(3):
    for b in range(3):
        for t in range(3): wand(h, b, t).save(ITEM / f'circuit_wand_{h*9+b*3+t}.png')
wand(0, 0, 0).save(ITEM / 'circuit_wand.png')

# Loose components use the same material families as their assembled silhouettes.
names=('quartz_head','amethyst_head','copper_head','oak_body','iron_body','blaze_body','wood_tail','iron_tail','diamond_tail')
colors=('#d4e7df','#af8bcf','#bd7a4f','#a77c4c','#a3b3b4','#e8a447','#997344','#9daeb1','#78c6c3')
for i,(name,color) in enumerate(zip(names,colors)):
    im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
    if i < 3:
        d.polygon(((16,2),(24,11),(22,22),(16,28),(9,22),(7,11)),fill='#302d33',outline='#b89b62')
        if i == 2:
            d.line((8,9,9,20,16,25,23,20,24,9),fill=color,width=3)
            d.polygon(((16,9),(20,15),(16,21),(12,15)),fill='#8cc9b8')
        else:
            d.polygon(((16,4),(21,12),(19,21),(16,25),(11,20),(11,12)),fill=color)
            d.line((16,6,16,19),fill='#edf4e6');d.line((16,7,20,13),fill='#bfa4df' if i==1 else '#a0c8c5')
        d.rectangle((12,26,20,28),fill='#b89b62')
    elif i < 6:
        d.line((7,26,25,7),fill='#262725',width=6);d.line((7,25,24,7),fill=color,width=4)
        d.line((7,24,23,7),fill='#dfc28c' if i!=4 else '#d4ddcf',width=1)
        for x,y in ((8,24),(14,18),(20,12)):d.line((x-2,y-2,x+2,y+2),fill='#766144',width=2)
    else:
        d.polygon(((16,4),(26,14),(19,27),(8,24),(5,14)),fill='#2c302b',outline='#c0a36d')
        d.polygon(((16,7),(23,14),(18,24),(10,21),(8,14)),fill=color)
        d.line((11,13,16,8,20,12),fill='#e6dfb1' if i!=8 else '#d7f6dd')
        d.line((7,17,21,23),fill='#8a7551',width=2)
    im.save(ITEM/f'{name}.png')

for name, color, emblem in [('blueprint_notebook', '#3a6665', 'plan'), ('spellbook', '#554365', 'spell')]:
    im = Image.new('RGBA', (32, 32)); d = ImageDraw.Draw(im)
    d.polygon(((5, 4), (25, 2), (28, 5), (28, 26), (9, 29), (5, 26)), fill='#191e20')
    d.polygon(((8, 5), (26, 4), (26, 24), (9, 27)), fill='#c6b897')
    for y in (22, 24, 26): d.line((10, y, 25, y-2), fill='#8c826c')
    d.polygon(((5, 3), (24, 1), (27, 4), (27, 23), (8, 26), (5, 23)), fill=color, outline='#252a28')
    d.line((8, 4, 8, 24), fill='#b79c64'); d.line((10, 4, 24, 3), fill='#749286' if emblem == 'plan' else '#887393')
    for y in (6, 20): d.rectangle((5, y, 9, y+1), fill='#bc9b60')
    if emblem == 'plan':
        d.rectangle((13, 8, 22, 18), outline='#cfceb0'); d.line((14, 16, 19, 10, 21, 15, 14, 16), fill='#8dc4b9')
    else:
        d.polygon(((18, 7), (23, 12), (18, 18), (12, 12)), outline='#d5b779'); d.line((14, 13, 21, 11), fill='#aba3cb'); d.point((18, 12), fill='#eaf3d4')
    d.rectangle((24, 12, 29, 14), fill='#c5a466'); d.point((27, 12), fill='#f0d89b')
    im.save(ITEM / f'{name}.png')

for name, base, accent in [('wood_plate', '#664b34', '#c09b63'), ('brass_plate', '#78623a', '#e4c27f'), ('crystal_plate', '#416374', '#a9ddd9')]:
    im = Image.new('RGBA', (32, 32)); d = ImageDraw.Draw(im)
    points=[(16, 2), (28, 9), (28, 23), (16, 30), (4, 23), (4, 9)]
    d.polygon([(x, min(31,y+1)) for x,y in points], fill='#202423')
    d.polygon(points, fill=base, outline='#2b2b25')
    d.polygon([(16, 4), (26, 10), (26, 22), (16, 28), (6, 22), (6, 10)], outline=accent)
    for x,y in ((16,6),(24,11),(24,21),(16,26),(8,21),(8,11)):d.point((x,y), fill=accent)
    d.polygon([(16,9),(22,13),(22,20),(16,24),(10,20),(10,13)], outline='#b1bc9a' if name == 'crystal_plate' else '#b2925e')
    d.line((12,19,16,12,20,19,12,19), fill=accent)
    im.save(ITEM / f'{name}.png')

# Both tables get recognisable timber, hardware and their own working surface.
BLOCK = ROOT / 'textures/block'
for kind in ('inscription_table', 'wand_workbench'):
    im = Image.new('RGB', (32,32), '#544032'); d=ImageDraw.Draw(im)
    for y in range(32):
        if y % 8 == 0:d.line((0,y,31,y),fill='#302d26')
        elif y % 8 == 1:d.line((1,y,30,y),fill='#71553a')
    d.rectangle((1,1,30,30),outline='#a98b56');d.rectangle((3,3,28,28),outline='#302d29')
    for x,y in ((2,2),(29,2),(2,29),(29,29)):d.point((x,y),fill='#e1c28a')
    if kind == 'inscription_table':
        d.rectangle((5,5,26,26),fill='#293231',outline='#786c4b')
        d.polygon(((16,7),(24,12),(24,20),(16,25),(8,20),(8,12)),outline='#c5ae74')
        d.line((8,12,24,20,16,7,16,25,24,12,8,20),fill='#6d9f94')
    else:
        d.rectangle((5,6,24,24),fill='#b8a883',outline='#403d30')
        d.line((8,20,19,9),fill='#536257',width=3);d.line((8,20,19,9),fill='#8b6b42')
        d.polygon(((19,6),(22,9),(20,12),(17,10)),fill='#cae3d2',outline='#6b755c')
        d.rectangle((25,9,29,21),fill='#656d69'); d.line((26,9,26,20),fill='#a8b3a7')
    im.save(BLOCK / f'{kind}_top.png')
    side=Image.new('RGB',(32,32),'#45382d');s=ImageDraw.Draw(side)
    for x in (0,8,16,24,31):s.line((x,0,x,31),fill='#2b2b25')
    s.rectangle((0,0,31,4),fill='#776043');s.line((0,1,31,1),fill='#b69b69')
    s.rectangle((4,9,27,23),fill='#302f28',outline='#89734e')
    for x in (7,24):s.rectangle((x,13,x+1,19),fill='#bb9d65')
    s.line((5,25,26,25),fill='#272b26');s.rectangle((0,29,31,31),fill='#282b26')
    side.save(BLOCK/f'{kind}_side.png')
    model=ROOT/f'models/block/{kind}.json'
    data=json.loads(model.read_text(encoding='utf-8'))
    if kind == 'inscription_table':
        for face in ('north','south','east','west'):data['textures'][face]=f'elementalcircuits:block/{kind}_side'
    else:data['textures']['side']=f'elementalcircuits:block/{kind}_side'
    model.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

for face in ('top','front','side'):
    im=Image.new('RGB',(32,32),'#39413e');d=ImageDraw.Draw(im)
    d.rectangle((0,0,31,31),outline='#272b27');d.rectangle((2,2,29,29),outline='#9b8252');d.line((3,3,28,3),fill='#c5aa71')
    for x,y in ((4,4),(27,4),(4,27),(27,27)):d.rectangle((x,y,x+1,y+1),fill='#c0a676')
    if face=='top':
        d.polygon(((16,5),(25,10),(25,21),(16,27),(7,21),(7,10)),outline='#c6b47d')
        d.line((7,10,25,21,16,5,16,27,25,10,7,21),fill='#719b8e')
    elif face=='front':
        d.rectangle((7,7,24,24),fill='#242f2d',outline='#71674c')
        d.polygon(((16,9),(22,17),(18,17),(18,24),(14,24),(14,17),(10,17)),fill='#9ec7b1',outline='#c8b77b')
    else:
        for y in (10,14,18,22):d.line((8,y,23,y),fill='#202b28');d.line((8,y+1,23,y+1),fill='#566354')
    im.save(BLOCK/f'fixed_{face}.png')

print('Baked workshop skin, engraving, 27 wands, books, plates and table surfaces')
