package dev.elementalcircuits.domain.combat;

/** Stable resource suffixes, independent of enum ordinals and Minecraft registries. */
public enum DamageAffinity {
    PURE("pure"),
    EARTH("earth"),
    WATER("water"),
    FIRE("fire"),
    WIND("wind"),
    LIGHTNING("lightning"),
    LIGHT("light"),
    SHADOW("shadow"),
    ARCANE("arcane"),
    FROST("frost"),
    MIXED("mixed");
    private final String path;

    DamageAffinity(String path) {
        this.path = path;
    }

    public String path() {
        return "ec_damage/" + path;
    }

    public static DamageAffinity of(Element element, boolean inverse) {
        return element == Element.FIRE && inverse ? FROST : valueOf(element.name());
    }
}
