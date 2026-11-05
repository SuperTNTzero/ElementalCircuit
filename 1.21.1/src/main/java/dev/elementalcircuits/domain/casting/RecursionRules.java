package dev.elementalcircuits.domain.casting;

import dev.elementalcircuits.domain.Fixed;

/** Frozen execution limits; live world scheduling budgets remain in the adapter. */
public record RecursionRules(
        int version, int maxDepth, int activations, int jobTicks, long retention) {
    public RecursionRules {
        if (version != 1
                || maxDepth < 1
                || maxDepth > 16
                || activations < 1
                || activations > 1024
                || jobTicks < 1
                || jobTicks > 1200
                || retention < 1
                || retention > Fixed.ONE)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._recursion_rules.001"));
    }

    public static RecursionRules defaults() {
        return new RecursionRules(1, 4, 64, 120, 62259);
    }
}
