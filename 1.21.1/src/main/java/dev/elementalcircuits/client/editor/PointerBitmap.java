package dev.elementalcircuits.client.editor;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

/** Cached Inkscape-exported cursor masks; only tinting happens when creating an OS cursor. */
public final class PointerBitmap {
    private record Sheet(int width, int[] pixels) {}

    private static final Map<Integer, Sheet> SHEETS = new ConcurrentHashMap<>();
    private final int size, hotX, hotY;
    private final int[] pixels;

    public PointerBitmap(EditorPointer pointer, int scale) {
        if (scale < 1 || scale > 3)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._pointer_bitmap.001"));
        size = 24 * scale;
        pixels = new int[size * size];
        hotX =
                (pointer.kind() == EditorPointer.Kind.SELECT
                                ? 5
                                : pointer.kind() == EditorPointer.Kind.ROUTE ? 4 : 12)
                        * scale;
        hotY =
                (pointer.kind() == EditorPointer.Kind.SELECT
                                ? 3
                                : pointer.kind() == EditorPointer.Kind.ROUTE ? 20 : 12)
                        * scale;
        var sheet = SHEETS.computeIfAbsent(scale, PointerBitmap::load);
        int index = RuneShapes.glyph(pointer.glyphKey()).index();
        int x = index % RuneShapes.COLUMNS * size;
        int y = index / RuneShapes.COLUMNS * size;
        for (int row = 0; row < size; row++)
            for (int col = 0; col < size; col++)
                pixels[row * size + col] =
                        tint(sheet.pixels[(y + row) * sheet.width + x + col], pointer.color());
    }

    private static Sheet load(int scale) {
        String resource = "/assets/elementalcircuits/textures/gui/glyph-cursors-" + scale + ".png";
        try (var input = PointerBitmap.class.getResourceAsStream(resource)) {
            if (input == null)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.editor._pointer_bitmap.002")
                                + resource);
            var image = ImageIO.read(input);
            int width = RuneShapes.COLUMNS * 24 * scale;
            int height = RuneShapes.HEIGHT / RuneShapes.CELL * 24 * scale;
            if (image == null || image.getWidth() != width || image.getHeight() != height)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.editor._pointer_bitmap.003"));
            return new Sheet(width, image.getRGB(0, 0, width, height, null, 0, width));
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._pointer_bitmap.004"),
                    e);
        }
    }

    private static int tint(int source, int color) {
        int alpha = source >>> 24;
        if (alpha == 0) return 0;
        // The SVG exports a dark outline under a white foreground, including antialiased joins.
        int weight = Math.clamp((((source >>> 16) & 255) - 8) * 255 / 247, 0, 255);
        int red = (8 * (255 - weight) + (color >>> 16 & 255) * weight + 127) / 255;
        int green = (16 * (255 - weight) + (color >>> 8 & 255) * weight + 127) / 255;
        int blue = (24 * (255 - weight) + (color & 255) * weight + 127) / 255;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    public int size() {
        return size;
    }

    public int hotX() {
        return hotX;
    }

    public int hotY() {
        return hotY;
    }

    public int[] pixels() {
        return pixels.clone();
    }
}
