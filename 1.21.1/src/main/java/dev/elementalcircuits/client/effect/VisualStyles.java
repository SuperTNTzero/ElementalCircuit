package dev.elementalcircuits.client.effect;

import com.google.gson.*;

import dev.elementalcircuits.application.observation.SpellVisual;
import dev.elementalcircuits.domain.combat.Element;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Validated, atomic art reload. These numbers never enter the simulation. */
public final class VisualStyles {
    public static final int BASE_SPRITES = 16;

    public record Style(int sprite, int ticks, float width, float opacity, int copies) {
        public Style {
            if (sprite < 0
                    || sprite >= BASE_SPRITES
                    || ticks < 1
                    || ticks > 20
                    || !finite(width, .001f, 2)
                    || !finite(opacity, 0, 1)
                    || copies < 1
                    || copies > 16)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.effect._visual_styles.001"));
        }
    }

    public record ElementStyle(
            int sprite, int color, int core, int particles, float spread, float rise, float twist) {
        public ElementStyle {
            if (sprite < 0
                    || sprite >= BASE_SPRITES
                    || color < 0
                    || color > 0xffffff
                    || core < 0
                    || core > 0xffffff
                    || particles < 1
                    || particles > 16
                    || !finite(spread, .1f, 3)
                    || !finite(rise, -3, 3)
                    || !finite(twist, -4, 4))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.effect._visual_styles.002"));
        }
    }

    private record State(
            Map<SpellVisual.Kind, Style> styles, Map<Element, ElementStyle> elements) {}

    private static volatile State state;

    static {
        try (var in =
                VisualStyles.class.getResourceAsStream(
                        "/assets/elementalcircuits/art/effects.json")) {
            if (in == null)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.effect._visual_styles.003"));
            load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private VisualStyles() {}

    private static boolean finite(float value, float min, float max) {
        return Float.isFinite(value) && value >= min && value <= max;
    }

    private static int integer(JsonObject o, String key) {
        return o.get(key).getAsBigDecimal().intValueExact();
    }

    public static void load(Reader reader) {
        var root = JsonParser.parseReader(reader).getAsJsonObject();
        if (integer(root, "version") != 2)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.effect._visual_styles.004"));
        var styles = new EnumMap<SpellVisual.Kind, Style>(SpellVisual.Kind.class);
        for (var kind : SpellVisual.Kind.values()) {
            var s = root.getAsJsonObject("styles").getAsJsonObject(kind.name());
            styles.put(
                    kind,
                    new Style(
                            integer(s, "sprite"),
                            integer(s, "ticks"),
                            s.get("width").getAsFloat(),
                            s.get("opacity").getAsFloat(),
                            integer(s, "copies")));
        }
        var elements = new EnumMap<Element, ElementStyle>(Element.class);
        for (var e : Element.values()) {
            var s = root.getAsJsonObject("elements").getAsJsonObject(e.name());
            elements.put(
                    e,
                    new ElementStyle(
                            integer(s, "sprite"),
                            Integer.parseInt(s.get("color").getAsString(), 16),
                            Integer.parseInt(s.get("core").getAsString(), 16),
                            integer(s, "particles"),
                            s.get("spread").getAsFloat(),
                            s.get("rise").getAsFloat(),
                            s.get("twist").getAsFloat()));
        }
        state = new State(Map.copyOf(styles), Map.copyOf(elements));
    }

    public static Style of(SpellVisual.Kind kind) {
        return state.styles.get(kind);
    }

    public static ElementStyle of(Element element) {
        return state.elements.get(element);
    }
}
