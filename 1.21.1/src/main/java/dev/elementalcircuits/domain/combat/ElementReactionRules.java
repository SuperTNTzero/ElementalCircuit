package dev.elementalcircuits.domain.combat;

import dev.elementalcircuits.domain.mana.Mixture;

import java.util.*;

/** Versioned one-to-one opposition table; the queue chooses the oldest eligible counterpart. */
public record ElementReactionRules(int version, List<Counter> counters) {
    public record Counter(int a, int b) {
        public Counter {
            if (a < 1 || b < 1 || a >= Mixture.COMPONENTS || b >= Mixture.COMPONENTS || a == b)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.combat._element_reaction_rules.001"));
        }
    }

    public ElementReactionRules {
        if ((version != 1 && version != 2) || counters == null || counters.size() > 120)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.combat._element_reaction_rules.002"));
        counters = List.copyOf(counters);
        var seen = new HashSet<String>();
        for (var c : counters)
            if (!seen.add(Math.min(c.a(), c.b()) + ":" + Math.max(c.a(), c.b())))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.combat._element_reaction_rules.003"));
    }

    public boolean opposed(int a, int b) {
        for (var c : counters)
            if (c.a() == a && c.b() == b || c.a() == b && c.b() == a) return true;
        return false;
    }

    /**
     * Frozen protocol fallback for source-compatible state builders, never a mutable server
     * setting.
     */
    public static ElementReactionRules standard() {
        var c = new ArrayList<Counter>();
        for (int i = 1; i < 17; i += 2) c.add(new Counter(i, i + 1));
        c.add(new Counter(5, 3));
        c.add(new Counter(1, 7));
        c.add(new Counter(11, 13));
        return new ElementReactionRules(1, c);
    }
}
