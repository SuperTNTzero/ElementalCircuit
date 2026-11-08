# Mana mineral and node textures

Editable 16×16 pixel-art sources for mana ore, crystals, and node blocks. The palette combines deep-blue crystals and brass with solid pixel clusters and native Minecraft block UVs. These are original textures, without copied Minecraft texture files.

Natural nodes place crystals inside a rock block. Artificial nodes use a brass base, corner fittings, and an upright faceted crystal. The mana crystal block is a full building block with full collision and occlusion.

## Regenerate and export

Run from the `1.21.1/` project directory:

```powershell
./tools/artwork/export-mana-blocks.ps1 -Aseprite '<path-to-aseprite.exe>'
```

The command runs `tools/artwork/draw-mana-blocks.py` to rebuild source PNGs and native JSON models, then uses Aseprite to save editable `.aseprite` files and export the runtime block textures. It requires Python with Pillow and Aseprite. Rebuilding replaces generated source images; export an edited `.aseprite` file directly to retain manual changes.

Runtime textures live under `src/main/resources/assets/elementalcircuits/textures/block/`. Minecraft bakes the native block models; gameplay does not generate textures or invoke artwork applications.
