package dev.elementalcircuits.domain.casting;

import dev.elementalcircuits.domain.Fixed;

/** Versioned spatial profile and bounded world-contact work for one-shot volumes. */
public record ShapeRules(
        int version,
        int fanDegrees,
        int fanReach,
        int fanHeight,
        int wallThickness,
        int slabHeight,
        int ringInner,
        int columnRadius,
        int crossWidth,
        int domeInner,
        int groundOffset,
        int blockProbes) {
    public ShapeRules {
        if (version != 1
                || fanDegrees < 20
                || fanDegrees > 100
                || fanReach < Fixed.ONE
                || fanReach > 3 * Fixed.ONE
                || blockProbes < 32
                || blockProbes > 1024)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._shape_rules.001"));
        for (int v :
                new int[] {
                    fanHeight,
                    wallThickness,
                    slabHeight,
                    ringInner,
                    columnRadius,
                    crossWidth,
                    domeInner,
                    groundOffset
                })
            if (v <= 0 || v >= Fixed.ONE)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.casting._shape_rules.002"));
    }

    public static ShapeRules protocolV1() {
        return new ShapeRules(
                1, 70, 131072, 16384, 40960, 32768, 42598, 32768, 13107, 49152, 49152, 512);
    }
}
