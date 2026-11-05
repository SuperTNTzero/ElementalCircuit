"""Anchor the shared OBJ grip at the native humanoid palm, in either hand.

The exported grip center is (0.5, 0.24, 0.5). ItemRenderer centers the mesh;
ItemInHandLayer then rotates X=-90, Y=180 and offsets (±1, 2, -10)/16.
Target palm center is (∓1, 9, 0)/16 relative to the arm bone. Keep this
calibration separate from GUI, ground and first-person presentation.
"""
import json
import math
from pathlib import Path


def held_transform():
    angle = math.radians(25)
    grip_y = (0.24 - 0.5) * 0.8
    return {
        "rotation": [25, 0, 0],
        "translation": [0, round((-0.125 - grip_y * math.cos(angle)) * 16, 6),
                        round((0.0625 - grip_y * math.sin(angle)) * 16, 6)],
        "scale": [0.8, 0.8, 0.8],
    }


if __name__ == "__main__":
    folder = Path(__file__).resolve().parents[2] / "src/main/resources/assets/elementalcircuits/models/item"
    for index in range(27):
        path = folder / f"circuit_wand_{index}.json"
        model = json.loads(path.read_text(encoding="utf-8"))
        for hand in ("righthand", "lefthand"):
            model["display"]["thirdperson_" + hand] = held_transform()
        path.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
    print("Anchored 27 wand assemblies to both native palms.")
