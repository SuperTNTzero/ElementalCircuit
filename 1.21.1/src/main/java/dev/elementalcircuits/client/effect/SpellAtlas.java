package dev.elementalcircuits.client.effect;

import dev.elementalcircuits.domain.combat.Element;

/** Offline packed CC0 art. Stable tile IDs; no frame images loaded or allocated while drawing. */
public final class SpellAtlas {
    public static final int COLUMNS = 8, SPRITES = 64, PIXELS = 1024;

    private SpellAtlas() {}

    public static int frame(Element element, float age) {
        if (!Float.isFinite(age))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.effect._spell_atlas.001"));
        int tick = (int) Math.floor(Math.max(0, age) / 2);
        return switch (element) {
            case FIRE -> 16 + tick % 8;
            case WATER -> 24 + tick % 8;
            case ARCANE -> 32 + tick % 8;
            case SHADOW -> 40 + tick % 8;
            case LIGHTNING -> 48 + tick % 4;
            case WIND -> 52 + tick % 4;
            case LIGHT -> 56 + tick % 4;
            case EARTH -> 60 + tick % 4;
            case PURE -> 12;
        };
    }

    public static boolean colored(Element e) {
        return e == Element.FIRE
                || e == Element.WATER
                || e == Element.SHADOW
                || e == Element.ARCANE;
    }
}
