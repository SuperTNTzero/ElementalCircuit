package dev.elementalcircuits.domain.circuit;

import dev.elementalcircuits.domain.Fixed;

/** Material rank is stable: wood=0, brass=1, crystal=2. Hard bounds are format guards. */
public record LayerRules(int initialDepth, long growth) {
    public LayerRules {
        if (initialDepth < 1 || initialDepth > 16 || growth < Fixed.ONE || growth > 4 * Fixed.ONE)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._layer_rules.001"));
    }

    public int depth(Rules.Material material) {
        long value = initialDepth * Fixed.ONE;
        for (int i = 0; i < material.ordinal(); i++)
            value = Math.multiplyExact(value, growth) / Fixed.ONE;
        return (int) Math.max(1, Math.min(16, value / Fixed.ONE));
    }

    public static LayerRules defaults() {
        return new LayerRules(1, 2 * Fixed.ONE);
    }
}
