"""Rebuild the hand-authored 16px monocle icon and native armor palette."""
from pathlib import Path
from PIL import Image, ImageDraw
ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/elementalcircuits/textures"
im = Image.new("RGBA", (16, 16))
p = ImageDraw.Draw(im)
p.line([(10, 6), (13, 6), (14, 7), (14, 9)], fill="#8c642c", width=1)
p.line([(11, 5), (13, 5)], fill="#edc66a", width=1)
p.polygon([(4, 2), (8, 2), (11, 5), (11, 8), (8, 11), (4, 11), (1, 8), (1, 5)], fill="#5c411e")
p.polygon([(4, 3), (8, 3), (10, 5), (10, 8), (8, 10), (4, 10), (2, 8), (2, 5)], fill="#edc66a")
p.polygon([(4, 4), (8, 4), (9, 5), (9, 8), (8, 9), (4, 9), (3, 8), (3, 5)], fill="#478b9f")
p.line([(4, 5), (7, 5)], fill="#c6eff2")
p.point((4, 6), fill="#b2dde6")
p.point((14, 8), fill="#f5d382")
im.save(ROOT / "item/circuit_monocle.png")
im = Image.new("RGBA", (32, 32), "#d3ac52")
p = ImageDraw.Draw(im)
p.rectangle((0, 16, 31, 31), fill="#5092a7")
p.line((1, 17, 2, 18), fill="#b7e5ed")
im.save(ROOT / "models/armor/circuit_monocle_layer_1.png")
