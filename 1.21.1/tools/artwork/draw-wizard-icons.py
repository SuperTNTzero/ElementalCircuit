"""Pixel-authored 16px inventory silhouettes; mirror the half-sprites exactly.

Runtime uses PNG only. Retain editable Aseprite documents alongside the art.
Run with --aseprite PATH to refresh the .aseprite sources as well.
"""
import argparse
import subprocess
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ITEMS = ROOT / "src/main/resources/assets/elementalcircuits/textures/item"
SOURCE = ROOT / "art/aseprite/wizard"
PALETTE = {
    ".": (0, 0, 0, 0), "O": "#101b2c", "D": "#192b48",
    "N": "#284368", "L": "#3c5e85", "B": "#b48b4b",
    "G": "#e1bc74", "C": "#39acdc", "I": "#95e7f8",
}
# Left half, from outside edge to center seam; one character = one pixel.
SPRITES = {
    "hat": [".......O", "......ON", "......ON", ".....ODN",
            ".....ODN", "....ODNL", "....ODNN", "...ODNNL",
            "...ODNNN", "..OBBBBC", "..OBGGGI", ".OODDDDC",
            "ONNNNNNN", ".OBBBBBB", "..OOOOOO", "........"],
    "robe": ["....OO..", "..OONDOO", ".ONDNBND", "ONDNBNDN",
             "ONDNBNDI", "ODDNBNDI", "ODDNBNDI", "ODDNBNDN",
             "OBBNBNDN", ".OOOBNDD", "...OBBBB", "...ODNNL",
             "...ODNNL", "...ODNNL", "...OBBBG", "...OOOOO"],
    "leggings": ["........", "...OOOOO", "..OBBBBB", "..OGGGGG",
                 "..ODNNNL", "..ODNNNL", "..ODNNNO", "..ODNLO.",
                 "..ODNLO.", "..ODNLO.", "..ODNLO.", "..ODNLO.",
                 "..OBBGO.", "..ODNDO.", "..OOOOO.", "........"],
    "boots": ["........", "........", "...OOOO.", "...ODNO.",
              "...ODIO.", "...ODIO.", "...ODCO.", "...ODNO.",
              "...OBGO.", "..OOBBO.", ".ODNNLO.", ".ODNNNO.",
              ".ODDDDO.", ".OOOOOO.", "........", "........"],
}


def main():
    args = argparse.ArgumentParser()
    args.add_argument("--aseprite", type=Path)
    opts = args.parse_args()
    SOURCE.mkdir(parents=True, exist_ok=True)
    preview = Image.new("RGBA", (520, 156), "#172334")
    draw = ImageDraw.Draw(preview)
    for index, (name, rows) in enumerate(SPRITES.items()):
        image = Image.new("RGBA", (16, 16))
        for y, half in enumerate(rows):
            if len(half) != 8:
                raise ValueError((name, y, half))
            for x, color in enumerate(half + half[::-1]):
                image.putpixel((x, y), Image.new("RGBA", (1, 1), PALETTE[color]).getpixel((0, 0)))
        path = ITEMS / f"wizard_{name}.png"
        image.save(path)
        if opts.aseprite:
            subprocess.run([str(opts.aseprite), "--batch", str(path), "--save-as",
                            str(SOURCE / f"wizard_{name}.aseprite")], check=True)
        preview.alpha_composite(image.resize((112, 112), Image.Resampling.NEAREST), (12 + index * 130, 12))
        draw.text((20 + index * 130, 134), name.upper(), fill="#e1bc74")
    target = ROOT / "docs/images/grip-art"
    target.mkdir(parents=True, exist_ok=True)
    preview.convert("RGB").save(target / "wizard-icons.png")
    print("Four symmetric native 16x16 inventory sprites exported.")


if __name__ == "__main__":
    main()
