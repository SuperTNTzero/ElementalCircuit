"""Build physical rune resources from the existing vector-exported glyph atlas."""
import json
import re
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/elementalcircuits'
DATA = RES / 'data/elementalcircuits'

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

glyphs = json.loads((ASSETS / 'art/glyphs.json').read_text(encoding='utf-8'))
atlas = Image.open(ASSETS / 'textures/gui/glyphs.png').convert('RGBA')
indices = {key: i for i, key in enumerate(glyphs['shapes'])}
source = (ROOT / 'src/main/java/dev/elementalcircuits/domain/circuit/Rune.java').read_text(encoding='utf-8')
kinds = re.findall(r'^        ([A-Z][A-Z_]+)\(', source, re.M)
elements = ['PURE', 'EARTH', 'WATER', 'FIRE', 'WIND', 'LIGHTNING', 'LIGHT', 'SHADOW', 'ARCANE']
colors = ['#bcc9de', '#b39258', '#5bafff', '#ff7846', '#9be7c5', '#e4bbff', '#ffec9d', '#9574ce', '#ff83c2']
locales = {lang: json.loads((ASSETS / f'lang/{lang}.json').read_text(encoding='utf-8')) for lang in ('zh_cn', 'en_us')}
for kind in kinds:
    for element in elements if kind == 'CONVERT' else [None]:
        key = 'rune_' + kind.lower() + ('_' + element.lower() if element else '')
        glyph = 'element/' + element if element else 'rune/' + kind
        index = indices[glyph]
        cell = glyphs['cell']
        x, y = index % glyphs['columns'] * cell, index // glyphs['columns'] * cell
        mask = atlas.crop((x, y, x + cell, y + cell)).resize((24, 24), Image.Resampling.LANCZOS).getchannel('A')
        icon = Image.new('RGBA', (32, 32))
        draw = ImageDraw.Draw(icon)
        draw.rectangle((2, 1, 29, 30), fill='#ab8b4a')
        draw.rectangle((3, 2, 28, 29), fill='#182b43')
        ink = Image.new('RGBA', (24, 24), colors[elements.index(element)] if element else '#91d8ef')
        icon.paste(ink, (4, 4), mask)
        path = ASSETS / f'textures/item/runes/{key}.png'
        path.parent.mkdir(parents=True, exist_ok=True)
        icon.save(path)
        write(ASSETS / f'models/item/{key}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'elementalcircuits:item/runes/{key}'}})
        for lang, strings in locales.items():
            title = strings[f'rune.elementalcircuits.{kind.lower()}.title']
            if element:
                title = strings[f'text.elementalcircuits.domain.combat._element.{elements.index(element)+1:03d}'] + ('转化符文' if lang == 'zh_cn' else ' Conversion Rune')
            else:
                title += '符文' if lang == 'zh_cn' else ' Rune'
            strings[f'item.elementalcircuits.{key}'] = title

for lang, strings in locales.items():
    extra = {
        'item.elementalcircuits.mana_dust': ('魔力粉', 'Mana Dust'),
        'block.elementalcircuits.rune_engraving_table': ('符文篆刻台', 'Rune Engraving Table'),
        'screen.elementalcircuits.rune_cost': ('纸 ×1 + 魔力粉 ×1', '1 Paper + 1 Mana Dust'),
        'message.elementalcircuits.engraving_materials': ('需要一张纸和一份魔力粉', 'Requires one paper and one mana dust'),
        'message.elementalcircuits.missing_rune': ('缺少 %s ×%s', 'Missing %s ×%s'),
        'configuration.elementalcircuits.require_runes.description': ('1：刻印或导入实际法阵时消耗新增符文；0：自由绘制。创造模式不消耗。导出和蓝图簿收录不消耗。', '1: consume added runes when inscribing or applying imported circuits; 0: draw freely. Creative players are exempt. Exporting or storing blueprints never consumes runes.'),
        'elementalcircuits.configuration.inscription.requireRunes': ('绘制消耗符文', 'Require Physical Runes'),
        'elementalcircuits.configuration.inscription.requireRunes.tooltip': ('1需要先篆刻符文；0可直接绘制。仅影响新增符文，移动、调参和导出不消耗。', '1 requires engraved runes; 0 allows free drawing. Only added runes are consumed; moving, editing parameters and exporting are free.')
    }
    strings.update({k: v[0 if lang == 'zh_cn' else 1] for k, v in extra.items()})
    write(ASSETS / f'lang/{lang}.json', strings)

write(DATA / 'recipe/mana_dust.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': [{'item': i} for i in ['minecraft:redstone', 'minecraft:glowstone_dust', 'elementalcircuits:mana_crystal']], 'result': {'id': 'elementalcircuits:mana_dust', 'count': 8}})
write(DATA / 'recipe/rune_engraving_table.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': ['PMP', 'CSC', 'S S'], 'key': {'P': {'item': 'minecraft:paper'}, 'M': {'item': 'elementalcircuits:mana_dust'}, 'C': {'item': 'minecraft:copper_ingot'}, 'S': {'item': 'minecraft:spruce_planks'}}, 'result': {'id': 'elementalcircuits:rune_engraving_table', 'count': 1}})
write(ASSETS / 'models/item/mana_dust.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'elementalcircuits:item/mana_dust'}})
icon = Image.new('RGBA', (16, 16))
draw = ImageDraw.Draw(icon)
draw.polygon([(2,12),(4,8),(7,8),(9,5),(12,9),(14,12),(11,14),(4,14)], fill='#35577c')
draw.line([(4,11),(7,10),(9,7),(11,10),(12,12)], fill='#8ce2ec', width=2)
icon.save(ASSETS / 'textures/item/mana_dust.png')
write(ASSETS / 'blockstates/rune_engraving_table.json', {'variants': {'': {'model': 'elementalcircuits:block/rune_engraving_table'}}})
write(ASSETS / 'models/block/rune_engraving_table.json', {'parent': 'minecraft:block/cube', 'textures': {'particle': 'minecraft:block/spruce_planks', 'down': 'minecraft:block/spruce_planks', 'up': 'elementalcircuits:block/rune_engraving_top', 'north': 'minecraft:block/cartography_table_side1', 'south': 'minecraft:block/cartography_table_side1', 'east': 'minecraft:block/cartography_table_side2', 'west': 'minecraft:block/cartography_table_side2'}})
write(ASSETS / 'models/item/rune_engraving_table.json', {'parent': 'elementalcircuits:block/rune_engraving_table'})
top = Image.new('RGBA', (32, 32), '#60452d')
draw = ImageDraw.Draw(top)
for y in range(0, 32, 8): draw.line((0,y,31,y), fill='#392e24')
draw.rectangle((2,2,29,29), outline='#ad8c4f', width=2)
draw.rectangle((7,5,24,26), fill='#cbbd98')
draw.rectangle((9,7,22,24), fill='#192d44')
draw.line((12,17,16,10,20,17,16,22,12,17), fill='#73d5e7', width=1)
top.save(ASSETS / 'textures/block/rune_engraving_top.png')
write(DATA / 'loot_table/blocks/rune_engraving_table.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'elementalcircuits:rune_engraving_table'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
print(f'Generated {len(kinds) - 1 + len(elements)} physical rune items')
