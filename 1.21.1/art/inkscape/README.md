# Vector rune artwork

[runes.svg](runes.svg) is the canonical editable source for rune, element, and tool glyphs. Open it in Inkscape and use the Layers and Objects panel to select a glyph. The source uses vector paths and ellipses with no embedded bitmaps.

## Editing contract

Preserve each layer's `id`, `data-ec-key`, `data-ec-index`, and outer layout transform. Glyph contents use normalized coordinates from -1 to 1. Paths and internal transforms can be edited; keep white strokes, a transparent background, and rounded joins. Do not add filters, text, external references, or raster images.

Indices determine atlas order. Adding a rune also requires its Java definition, item mapping, behavior, and contracts; adding a picture alone does not add a playable rune.

## Export

From the `1.21.1/` project directory:

```powershell
python -m pip install -r tools/artwork/requirements-glyphs.txt
python tools/artwork/bake-glyphs.py --inkscape '<path-to-inkscape.exe>'
```

The exporter requires Python, Pillow, fontTools, and Inkscape. Inkscape 1.4.4 is the reference version. The executable can also be selected with `INKSCAPE_BINARY`.

The script invokes Inkscape to render the transparent atlas and 24 / 48 / 72 pixel cursors. It checks cell bounds, transparency, and antialiasing before updating generated PNGs and metadata under `src/main/resources/assets/elementalcircuits/`, plus `art/glyphs.json`. That JSON records the source SHA-256 and discrete outlines used by geometry contracts; the outlines do not draw the glyphs.

Do not edit generated files directly. Runtime colors come from the client theme and element. Normal mod builds use the exported assets without requiring Inkscape.
