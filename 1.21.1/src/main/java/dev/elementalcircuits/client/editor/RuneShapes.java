package dev.elementalcircuits.client.editor;

import com.google.gson.*;

import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.combat.Element;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Immutable normalized art loaded from a checked-in vector resource; geometry is inspected,
 * rendering uses SVG-exported textures.
 */
public final class RuneShapes {
    public record Stroke(double x1, double y1, double x2, double y2) {}

    public record Glyph(int index, List<Stroke> strokes) {
        public Glyph {
            strokes = List.copyOf(strokes);
        }
    }

    private static final Map<String, Glyph> ART;
    public static final int CELL, COLUMNS, WIDTH, HEIGHT, PADDING;

    static {
        try (var in =
                RuneShapes.class.getResourceAsStream("/assets/elementalcircuits/art/glyphs.json")) {
            if (in == null)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.editor._rune_shapes.001"));
            var root =
                    JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                            .getAsJsonObject();
            if (root.get("version").getAsInt() != 2)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.editor._rune_shapes.002"));
            CELL = root.get("cell").getAsInt();
            COLUMNS = root.get("columns").getAsInt();
            WIDTH = root.get("atlasWidth").getAsInt();
            HEIGHT = root.get("atlasHeight").getAsInt();
            PADDING = root.get("padding").getAsInt();
            if (CELL < 16
                    || CELL > 256
                    || COLUMNS < 1
                    || WIDTH != CELL * COLUMNS
                    || HEIGHT < 1
                    || HEIGHT > 4096
                    || PADDING < 1
                    || PADDING >= CELL / 2)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.editor._rune_shapes.003"));
            var art = new LinkedHashMap<String, Glyph>();
            int index = 0;
            for (var entry : root.getAsJsonObject("shapes").entrySet()) {
                var strokes = new ArrayList<Stroke>();
                for (var value : entry.getValue().getAsJsonArray()) {
                    var row = value.getAsJsonArray();
                    if (row.size() != 4)
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.editor._rune_shapes.004"));
                    double[] c = new double[4];
                    for (int i = 0; i < 4; i++) {
                        c[i] = row.get(i).getAsDouble();
                        if (!Double.isFinite(c[i]) || Math.abs(c[i]) > 1)
                            throw new IllegalArgumentException(
                                    dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.editor._rune_shapes.005"));
                    }
                    strokes.add(new Stroke(c[0], c[1], c[2], c[3]));
                }
                if (strokes.isEmpty() || strokes.size() > 256)
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.editor._rune_shapes.006"));
                art.put(entry.getKey(), new Glyph(index++, strokes));
            }
            if (index > COLUMNS * (HEIGHT / CELL))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.editor._rune_shapes.007"));
            ART = Collections.unmodifiableMap(art);
            for (var kind : Rune.Kind.values())
                if (kind != Rune.Kind.CUSTOM_EFFECT) glyph("rune/" + kind.name());
            for (var element : Element.values()) glyph("element/" + element.name());
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private RuneShapes() {}

    public static Glyph glyph(String name) {
        return Objects.requireNonNull(
                ART.get(name),
                dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.editor._rune_shapes.008")
                        + name);
    }

    public static Set<String> names() {
        return ART.keySet();
    }

    public static String key(Rune.Kind kind, Element element) {
        if (kind == Rune.Kind.CUSTOM_EFFECT) return "rune/FRAGMENT";
        return kind == Rune.Kind.CONVERT && element != null
                ? "element/" + element.name()
                : "rune/" + kind.name();
    }

    public static List<Stroke> of(Rune.Kind kind) {
        return glyph(key(kind, null)).strokes();
    }

    public static List<Stroke> of(Rune.Kind kind, Element element) {
        return glyph(key(kind, element)).strokes();
    }

    public static List<Stroke> element(Element element) {
        return glyph("element/" + element.name()).strokes();
    }
}
