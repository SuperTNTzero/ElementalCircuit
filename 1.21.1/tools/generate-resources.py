"""Deterministic local placeholder pixel assets and vanilla-format data resources."""
import json
import gzip
import struct
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
MOD = "elementalcircuits"


def write(path, value):
    # Authored workstation geometry and material-aware carriers own these resources since 0.20.
    protected = {f"assets/{MOD}/{kind}/{name}.json" for kind in ("blockstates", "models/item") for name in ("inscription_table", "wand_workbench", "fixed_circuit")}
    if (path in protected or path in {f"data/{MOD}/recipe/inscription_table.json",f"data/{MOD}/recipe/wand_workbench.json"}) and (ROOT/path).exists(): return
    dest = ROOT / path
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


zh = {
    "itemGroup.elementalcircuits": "元素法阵",
    "item.elementalcircuits.circuit_wand": "刻阵法杖",
    "item.elementalcircuits.wood_plate": "木质法阵基板",
    "item.elementalcircuits.brass_plate": "黄铜法阵基板",
    "item.elementalcircuits.crystal_plate": "水晶法阵基板",
    "block.elementalcircuits.inscription_table": "法阵刻印台",
    "hint.elementalcircuits.wand": "右键：手动起阵 / 停止供给",
    "hint.elementalcircuits.wand_edit": "蹲下右键：打开六边形刻印界面",
    "hint.elementalcircuits.no_default_spell": "新法杖为空白；可在刻印界面载入示例，再保存",
}
en = dict(zip(zh, ["Elemental Circuits", "Inscribed Wand", "Wood Circuit Plate", "Brass Circuit Plate", "Crystal Circuit Plate", "Inscription Table", "Right-click: start / stop supply", "Sneak + right-click: open hex inscription", "New wands are blank. Load a sample and save it in the editor."]))
for language, data in [("zh_cn", zh), ("en_us", en)]:
    write(f"assets/{MOD}/lang/{language}.json", data)

for item in ["circuit_wand", "wood_plate", "brass_plate", "crystal_plate"]:
    write(f"assets/{MOD}/models/item/{item}.json", {"parent": "minecraft:item/handheld" if item == "circuit_wand" else "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{item}"}})
    im = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(im)
    if item == "circuit_wand":
        d.line((3, 14, 12, 5), fill="#534135", width=3)
        d.line((4, 13, 12, 5), fill="#b88b4d", width=1)
        d.polygon([(11, 1), (15, 3), (15, 6), (12, 8), (9, 6), (9, 3)], fill="#76c7dc", outline="#e3c586")
        d.point((12, 3), fill="#e6faff")
    else:
        color = {"wood_plate": "#986c42", "brass_plate": "#ccaa62", "crystal_plate": "#a2d6e7"}[item]
        d.polygon([(8, 1), (14, 4), (14, 11), (8, 15), (2, 11), (2, 4)], fill="#302b32", outline=color)
        d.polygon([(8, 3), (12, 5), (12, 10), (8, 13), (4, 10), (4, 5)], outline=color)
        d.line((5, 9, 8, 5, 11, 9, 5, 9), fill=color)
    dest = ROOT / f"assets/{MOD}/textures/item/{item}.png"
    dest.parent.mkdir(parents=True, exist_ok=True)
    im.save(dest)

table = "inscription_table"
write(f"assets/{MOD}/blockstates/{table}.json", {"variants": {"": {"model": f"{MOD}:block/{table}"}}})
write(f"assets/{MOD}/models/block/{table}.json", {"parent": "minecraft:block/cube", "textures": {"particle": "minecraft:block/oak_planks", "down": "minecraft:block/oak_planks", "up": f"{MOD}:block/{table}_top", **{side: "minecraft:block/bookshelf" for side in ["north", "south", "east", "west"]}}})
write(f"assets/{MOD}/models/item/{table}.json", {"parent": f"{MOD}:block/{table}"})
im = Image.new("RGB", (32, 32), "#302b31")
d = ImageDraw.Draw(im)
for inset, color in [(1, "#a78550"), (3, "#745840")]:
    d.rectangle((inset, inset, 31 - inset, 31 - inset), outline=color)
d.polygon([(16, 5), (26, 11), (26, 21), (16, 27), (6, 21), (6, 11)], outline="#d7ba7c")
d.line((6, 11, 26, 21, 16, 5, 16, 27, 26, 11, 6, 21, 6, 11), fill="#78b9c2")
dest = ROOT / f"assets/{MOD}/textures/block/{table}_top.png"
dest.parent.mkdir(parents=True, exist_ok=True)
im.save(dest)
write(f"data/{MOD}/loot_tables/blocks/{table}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{table}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
write("data/minecraft/tags/blocks/mineable/axe.json", {"replace": False, "values": [f"{MOD}:{table}"]})


def shaped(name, pattern, keys, count=1):
    recipe = {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern, "key": keys, "result": {"item": f"{MOD}:{name}", "count": count}}
    write(f"data/{MOD}/recipes/{name}.json", recipe)
    first = next(iter(keys.values()))
    write(f"data/{MOD}/advancements/recipes/{name}.json", {"parent": "minecraft:recipes/root", "criteria": {"has_material": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [first]}}}, "rewards": {"recipes": [f"{MOD}:{name}"]}})


shaped("circuit_wand", ["  H", " B ", "T  "], {"H": {"item": f"{MOD}:quartz_head"}, "B": {"item": f"{MOD}:oak_body"}, "T": {"item": f"{MOD}:wood_tail"}})
shaped("wood_plate", ["PP", "PP"], {"P": {"tag": "minecraft:planks"}}, 2)
shaped("brass_plate", ["CGC", "CRC", "CGC"], {"C": {"item": "minecraft:copper_ingot"}, "G": {"item": "minecraft:gold_nugget"}, "R": {"item": "minecraft:redstone"}}, 2)
shaped("crystal_plate", [" A ", "AGA", " A "], {"A": {"item": "minecraft:amethyst_shard"}, "G": {"item": "minecraft:glass"}}, 2)
shaped(table, [" B ", "PCP", "P P"], {"B": {"item": "minecraft:book"}, "P": {"tag": "minecraft:planks"}, "C": {"item": f"{MOD}:wood_plate"}})
print("Generated language, models, pixel textures, recipes, loot and tags.")

# Native Minecraft structure for isolated GameTests, shipped only as test resources.
def nbt_string(s):
    encoded = s.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def nbt_tag(kind, name, body):
    return bytes([kind]) + nbt_string(name) + body


structure = b"\x0a\x00\x00" + nbt_tag(3, "DataVersion", struct.pack(">i", 3465))
structure += nbt_tag(9, "size", b"\x03" + struct.pack(">iiii", 3, 10, 8, 10))
structure += nbt_tag(9, "palette", b"\x0a" + struct.pack(">i", 1) + nbt_tag(8, "Name", nbt_string("minecraft:air")) + b"\x00")
structure += nbt_tag(9, "blocks", b"\x0a" + struct.pack(">i", 0))
structure += nbt_tag(9, "entities", b"\x0a" + struct.pack(">i", 0)) + b"\x00"
test_structure = ROOT.parents[1] / "test/resources/data" / MOD / "structures/empty.nbt"
test_structure.parent.mkdir(parents=True, exist_ok=True)
test_structure.write_bytes(gzip.compress(structure, mtime=0))

# Physical components and 27 assembled wand appearances share the versioned part palette.
parts = json.loads((ROOT / f"data/{MOD}/rules/wand_parts.json").read_text(encoding="utf-8"))["parts"]
english = ["Quartz Wand Head", "Amethyst Wand Head", "Copper Wand Head", "Oak Wand Body", "Iron Wand Body", "Blaze Wand Body", "Wood Wand Tail", "Iron Wand Tail", "Diamond Wand Tail"]
materials = ["quartz", "amethyst_shard", "copper_ingot", "stick", "iron_ingot", "blaze_rod", "oak_planks", "iron_ingot", "diamond"]
for i, part in enumerate(parts):
    name, slot, color = part["id"], part["slot"], "#" + part["color"]
    zh[f"item.{MOD}.{name}"] = part["title"]
    en[f"item.{MOD}.{name}"] = english[i]
    write(f"assets/{MOD}/models/item/{name}.json", {"parent":"minecraft:item/generated", "textures":{"layer0":f"{MOD}:item/{name}"}})
    im=Image.new("RGBA",(16,16));d=ImageDraw.Draw(im)
    if slot=="HEAD": d.polygon([(8,1),(13,5),(12,11),(8,14),(4,11),(3,5)],fill=color,outline="#f2dfb4");d.line((8,3,8,11),fill="#ffffff")
    elif slot=="BODY": d.line((4,13,12,3),fill="#3d3430",width=5);d.line((4,13,12,3),fill=color,width=3)
    else: d.polygon([(4,8),(8,4),(13,9),(9,14)],fill=color,outline="#efe1bc")
    im.save(ROOT / f"assets/{MOD}/textures/item/{name}.png")
    shaped(name,[" M "," S "],{"M":{"item":"minecraft:"+materials[i]},"S":{"item":"minecraft:stick"}})
for lang,data in [("zh_cn",zh),("en_us",en)]:write(f"assets/{MOD}/lang/{lang}.json",data)
heads=[p for p in parts if p["slot"]=="HEAD"];bodies=[p for p in parts if p["slot"]=="BODY"];tails=[p for p in parts if p["slot"]=="TAIL"]
overrides=[]
for h,head in enumerate(heads):
    for b,body in enumerate(bodies):
        for t,tail in enumerate(tails):
            index=h*9+b*3+t
            name=f"circuit_wand_{index}"
            im=Image.new("RGBA",(16,16));d=ImageDraw.Draw(im)
            d.line((3,13,12,4),fill="#342e2c",width=4);d.line((3,13,12,4),fill="#"+body["color"],width=2)
            d.polygon([(11,1),(15,3),(15,6),(12,8),(9,6),(9,3)],fill="#"+head["color"],outline="#e3c586")
            d.polygon([(1,12),(3,10),(6,13),(4,15)],fill="#"+tail["color"],outline="#e3c586")
            im.save(ROOT/f"assets/{MOD}/textures/item/{name}.png")
            write(f"assets/{MOD}/models/item/{name}.json",{"parent":"minecraft:item/handheld","textures":{"layer0":f"{MOD}:item/{name}"}})
            overrides.append({"predicate":{f"{MOD}:assembly":index},"model":f"{MOD}:item/{name}"})
write(f"assets/{MOD}/models/item/circuit_wand.json",{"parent":"minecraft:item/handheld","textures":{"layer0":f"{MOD}:item/circuit_wand_0"},"overrides":overrides})

# Runtime-only light markers have no inventory item. Dimmed torches keep their shape;
# solid sources use a dark lamp texture while the original state remains encoded in the chunk.
write(f"assets/{MOD}/models/block/spell_light.json", {"textures":{"particle":"minecraft:block/glowstone"},"elements":[]})
write(f"assets/{MOD}/blockstates/spell_light.json", {"variants":{"":{"model":f"{MOD}:block/spell_light"}}})
write(f"assets/{MOD}/models/block/dimmed_light.json", {"parent":"minecraft:block/cube_all","textures":{"all":"minecraft:block/redstone_lamp"}})
multipart=[]
for source in ["torch","soul_torch","redstone_torch"]:
    multipart.append({"when":{"source":source},"apply":{"model":"minecraft:block/redstone_torch_off"}})
for source in ["wall_torch","soul_wall_torch","redstone_wall_torch"]:
    for facing,rotation in [("east",0),("south",90),("west",180),("north",270)]:
        multipart.append({"when":{"source":source,"facing":facing},"apply":{"model":"minecraft:block/redstone_wall_torch_off","y":rotation}})
for source in ["glowstone","sea_lantern","shroomlight","ochre_froglight","verdant_froglight","pearlescent_froglight","jack_o_lantern","redstone_lamp"]:
    multipart.append({"when":{"source":source},"apply":{"model":f"{MOD}:block/dimmed_light"}})
write(f"assets/{MOD}/blockstates/dimmed_light.json", {"multipart":multipart})
for language,names in [("zh_cn",["临时法术光源","被暗压制的光源"]),("en_us",["Temporary Spell Light","Dimmed Light Source"])]:
    dest=ROOT/f"assets/{MOD}/lang/{language}.json"
    data=json.loads(dest.read_text(encoding="utf-8"))
    for block,name in zip(["spell_light","dimmed_light"],names):data[f"block.{MOD}.{block}"]=name
    write(f"assets/{MOD}/lang/{language}.json",data)

# Fixed carrier: explicit facing, finite local supply and no vanilla NBT-copy loot duplication.
fixed="fixed_circuit"
write(f"assets/{MOD}/models/block/{fixed}.json",{"parent":"minecraft:block/orientable","textures":{"top":f"{MOD}:block/fixed_top","front":f"{MOD}:block/fixed_front","side":f"{MOD}:block/fixed_side"}})
write(f"assets/{MOD}/models/item/{fixed}.json",{"parent":f"{MOD}:block/{fixed}"})
write(f"assets/{MOD}/blockstates/{fixed}.json",{"variants":{f"facing={face}":{"model":f"{MOD}:block/{fixed}","y":angle} for face,angle in [("north",0),("east",90),("south",180),("west",270)]}})
for face in ["top","front","side"]:
    im=Image.new("RGBA",(16,16),"#263440");d=ImageDraw.Draw(im)
    d.rectangle((0,0,15,15),outline="#bfa06c");d.rectangle((2,2,13,13),outline="#586b78")
    if face=="top":
        d.polygon([(8,2),(13,5),(13,10),(8,13),(3,10),(3,5)],outline="#e5c886")
        d.line((4,5,11,10,8,4,4,10,11,5),fill="#71c9e6",width=1)
    elif face=="front":
        d.polygon([(8,3),(12,9),(9,9),(9,13),(6,13),(6,9),(3,9)],fill="#80d4ed",outline="#e5c886")
    else:
        d.rectangle((4,5,11,10),outline="#ad8b53");d.line((6,7,9,7),fill="#80d4ed")
    im.save(ROOT/f"assets/{MOD}/textures/block/fixed_{face}.png")
# Fixed circuits are made by interacting with the inscription table using a physical plate.
(ROOT/f"data/{MOD}/recipe/{fixed}.json").unlink(missing_ok=True)
write("data/minecraft/tags/blocks/mineable/pickaxe.json",{"replace":False,"values":[f"{MOD}:{fixed}"]})
for language,name in [("zh_cn","固定法阵"),("en_us","Fixed Circuit")]:
    dest=ROOT/f"assets/{MOD}/lang/{language}.json";data=json.loads(dest.read_text(encoding="utf-8"));data[f"block.{MOD}.{fixed}"]=name;write(f"assets/{MOD}/lang/{language}.json",data)
# Separate physical crafting surfaces and book covers.
workshop="wand_workbench"
write(f"assets/{MOD}/blockstates/{workshop}.json",{"variants":{"":{"model":f"{MOD}:block/{workshop}"}}})
write(f"assets/{MOD}/models/block/{workshop}.json",{"parent":"minecraft:block/cube_bottom_top","textures":{"top":f"{MOD}:block/{workshop}_top","bottom":"minecraft:block/oak_planks","side":"minecraft:block/crafting_table_side"}})
write(f"assets/{MOD}/models/item/{workshop}.json",{"parent":f"{MOD}:block/{workshop}"})
im=Image.new("RGB",(32,32),"#40352d");d=ImageDraw.Draw(im)
d.rectangle((0,0,31,31),outline="#d8b174",width=2)
for x,c in [(7,"#acdfeb"),(15,"#cb985b"),(23,"#9eabb8")]:
    d.rectangle((x-2,8,x+2,24),fill=c);d.polygon([(x,4),(x+4,7),(x,11),(x-4,7)],outline="#ebd7a4")
im.save(ROOT/f"assets/{MOD}/textures/block/{workshop}_top.png")
shaped(workshop,[" Q ","CWC","PPP"],{"Q":{"item":"minecraft:quartz"},"C":{"item":"minecraft:copper_ingot"},"W":{"item":"minecraft:crafting_table"},"P":{"tag":"minecraft:planks"}})
for name,color,symbol in [("blueprint_notebook","#436c91","plan"),("spellbook","#705196","rune")]:
    actual="spellbook" if name=="spellbook" else name
    write(f"assets/{MOD}/models/item/{actual}.json",{"parent":"minecraft:item/generated","textures":{"layer0":f"{MOD}:item/{actual}"}})
    im=Image.new("RGBA",(16,16));d=ImageDraw.Draw(im);d.rectangle((2,1,13,14),fill=color,outline="#e3c586");d.rectangle((3,12,12,13),fill="#eddec4");d.line((4,2,4,11),fill="#d7bb7e")
    if symbol=="plan":d.rectangle((6,4,11,9),outline="#c7e8f4");d.line((7,8,10,5),fill="#c7e8f4")
    else:d.polygon([(9,3),(12,6),(9,9),(6,6)],outline="#b9f0e8");d.point((9,6),fill="#faf3b5")
    im.save(ROOT/f"assets/{MOD}/textures/item/{actual}.png")
shaped("blueprint_notebook",[" P "," B "," W "],{"P":{"item":"minecraft:paper"},"B":{"item":"minecraft:book"},"W":{"item":f"{MOD}:wood_plate"}})
shaped("spellbook",[" A "," B "," W "],{"A":{"item":"minecraft:amethyst_shard"},"B":{"item":"minecraft:book"},"W":{"item":f"{MOD}:wood_plate"}})
write(f"data/{MOD}/loot_tables/blocks/{workshop}.json",{"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":f"{MOD}:{workshop}"}],"conditions":[{"condition":"minecraft:survives_explosion"}]}]})
write("data/minecraft/tags/blocks/mineable/axe.json",{"replace":False,"values":[f"{MOD}:inscription_table",f"{MOD}:{workshop}"]})
for language,labels in [("zh_cn",["法杖工作台","蓝图簿","法术书","元素法阵","上一法阵（副手法术书）","下一法阵（副手法术书）","打开法术书"]),("en_us",["Wand Workbench","Blueprint Notebook","Spellbook","Elemental Circuits","Previous circuit (offhand spellbook)","Next circuit (offhand spellbook)","Open Spellbook"])]:
    dest=ROOT/f"assets/{MOD}/lang/{language}.json";data=json.loads(dest.read_text(encoding="utf-8"))
    for key,label in zip([f"block.{MOD}.wand_workbench",f"item.{MOD}.blueprint_notebook",f"item.{MOD}.spellbook",f"key.categories.{MOD}",f"key.{MOD}.previous_spell",f"key.{MOD}.next_spell",f"key.{MOD}.open_spellbook"],labels):data[key]=label
    write(f"assets/{MOD}/lang/{language}.json",data)
