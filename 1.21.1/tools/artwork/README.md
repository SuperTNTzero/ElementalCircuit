# Artwork tools

These scripts generate or export Elemental Circuits models, textures, glyphs, and sounds. Run them from the `1.21.1/` project directory. Normal Gradle builds use checked-in runtime assets and do not require Blender, Aseprite, or Inkscape.

## Script families

| Entry | Purpose |
| --- | --- |
| `model-*-blender.py` | Wand, book, workstation, fixed-circuit, robe, and guide models |
| `paint-*.lua`, `draw-*.py`, `paint-guide-atlas.py` | Pixel-art sources and texture atlases |
| `bake-*.py`, `generate-resources.py` | Runtime resource exports |
| `bake-glyphs.py`, `requirements-glyphs.txt` | Inkscape SVG atlas and cursor export dependencies |
| `bake-audio.py`, `requirements-audio.txt` | Spell audio mixing and export dependencies |
| `blender_mesh_atlas.py`, `unwrap-wand-materials.py` | Mesh UV and atlas helpers |
| `align-wand-grip.py` | Wand grip transforms |
| `check-workstations.py` | Workstation asset checks |
| `blender-mcp-*.py` | Optional local Blender bridge utilities |
| `bake-mana-ecology.py` | Mana slime, gel, floating-node halo, and restoration-effect textures |

## Example: guide model and atlas

Install Blender, Aseprite, Python, and the Python packages required by the selected script. Replace the executable paths with your installations:

```powershell
python tools/artwork/paint-guide-atlas.py
& '<path-to-aseprite.exe>' -b art/aseprite/guidebook/guide_atlas.png --save-as art/aseprite/guidebook/guide_atlas.aseprite
& '<path-to-aseprite.exe>' -b art/aseprite/guidebook/guide_atlas.aseprite --save-as src/main/resources/assets/elementalcircuits/textures/item/guide_atlas.png
& '<path-to-blender.exe>' -b --python tools/artwork/model-guide-blender.py
python tools/artwork/bake-guidebook.py
```

Model scripts save the editable Blender source and the corresponding model exports. Painting scripts recreate source PNGs; export an edited Aseprite file directly if you want to preserve hand edits. Generators may overwrite their outputs, so run only the scripts for the assets you intend to regenerate.

See [vector runes](../../art/inkscape/README.md), [mana blocks](../../art/aseprite/mana-blocks/README.md), and [guide artwork](../../art/aseprite/guidebook/README.md) for source contracts. Keep third-party license and modification notices from [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md) with adapted assets.
