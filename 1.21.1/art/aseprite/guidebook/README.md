# Circuit Field Guide artwork

The inventory cover is original 16×16 pixel art: deep-blue leather, brass corners, a small crystal, and pale page edges, arranged in a diagonal book silhouette. The separate 3D atlas supplies the open guide model shown while held.

| File | Purpose |
| --- | --- |
| `circuit_guide.aseprite` | Editable inventory-cover source |
| `circuit_guide.png` | Inventory-cover source PNG |
| `cover-preview.png` | Nearest-neighbor enlargement; excluded from the runtime mod |
| `guide_atlas.aseprite`, `guide_atlas.png` | Editable atlas and PNG for the held 3D guide |

The runtime inventory texture is `src/main/resources/assets/elementalcircuits/textures/item/circuit_guide.png`. The 3D atlas is exported beside it as `guide_atlas.png`.

From the `1.21.1/` project directory, `python tools/artwork/bake-guidebook.py` generates the cover source and bilingual guide content. `python tools/artwork/paint-guide-atlas.py` generates the 3D atlas source. Aseprite batch export preserves editable project files and produces the game textures; see the [artwork commands](../../../tools/artwork/README.md).

The reader uses Minecraft or Patchouli page textures at runtime. Their GUI textures are not copied into this project. A normal mod build uses the checked-in exports and does not require Aseprite.
