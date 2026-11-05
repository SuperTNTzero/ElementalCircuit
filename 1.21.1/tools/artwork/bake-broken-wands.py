"""Bake inactive, scorched palettes for the existing 27 wand assemblies.

Retain each component's geometry, UVs and held transforms. A failed circuit board
does not break the physical staff. No new model geometry or runtime VFX is needed.
"""
from pathlib import Path
import json
from PIL import Image, ImageDraw

ASSETS = Path(__file__).resolve().parents[2] / "src/main/resources/assets/elementalcircuits"
MODELS = ASSETS / "models/item"
TEXTURES = ASSETS / "textures/item/wand"

for palette in range(16):
    image = Image.open(TEXTURES / f"palette_{palette}.png").convert("RGBA")
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = image.getpixel((x, y))
            grey = (r * 3 + g * 5 + b * 2) / 10
            image.putpixel((x, y), (int(r * .24 + grey * .24),
                                  int(g * .22 + grey * .23),
                                  int(b * .20 + grey * .22), a))
    draw = ImageDraw.Draw(image)
    # Coarse asymmetric fissures distinguish inactive crystals even at GUI scale.
    draw.line([(5, 0), (9, 7), (7, 12), (16, 18), (14, 23), (21, 31)],
              fill=(23, 24, 28, 255), width=2)
    draw.line([(8, 12), (3, 15), (0, 21)], fill=(25, 25, 29, 255), width=1)
    image.save(TEXTURES / f"inactive_{palette}.png")

for variant in range(27):
    model = json.loads((MODELS / f"circuit_wand_{variant}.json").read_text(encoding="utf-8"))
    for palette in range(16):
        model["textures"][f"p{palette}"] = f"elementalcircuits:item/wand/inactive_{palette}"
    model["textures"]["particle"] = "elementalcircuits:item/wand/inactive_1"
    (MODELS / f"circuit_wand_broken_{variant}.json").write_text(
        json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

root = json.loads((MODELS / "circuit_wand.json").read_text(encoding="utf-8"))
root["overrides"] = [
    {"predicate": {"elementalcircuits:assembly": i},
     "model": f"elementalcircuits:item/circuit_wand_{i}"} for i in range(27)] + [
    {"predicate": {"elementalcircuits:assembly": i + 27},
     "model": f"elementalcircuits:item/circuit_wand_broken_{i}"} for i in range(27)]
(MODELS / "circuit_wand.json").write_text(
    json.dumps(root, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("Baked 27 inactive assemblies with shared geometry and 16 static palettes.")
