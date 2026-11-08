package dev.elementalcircuits.domain.circuit;

import dev.elementalcircuits.domain.combat.Element;

import java.util.*;

/** Physical rune identity excludes editable parameters, position and polarity. */
public final class RuneMaterials {
    private RuneMaterials() {}

    public static String key(Rune.Kind kind, Element element) {
        return "rune_"
                + kind.name().toLowerCase(Locale.ROOT)
                + (kind == Rune.Kind.CONVERT ? "_" + element.name().toLowerCase(Locale.ROOT) : "");
    }

    public static Map<String, Integer> counts(Blueprint blueprint) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (var rune : blueprint.runes())
            counts.merge(key(rune.kind(), rune.element()), 1, Integer::sum);
        return counts;
    }

    public static Map<String, Integer> additions(Blueprint before, Blueprint after) {
        var old = counts(before);
        var added = counts(after);
        added.replaceAll((key, count) -> Math.max(0, count - old.getOrDefault(key, 0)));
        added.values().removeIf(count -> count == 0);
        return Collections.unmodifiableMap(added);
    }
}
