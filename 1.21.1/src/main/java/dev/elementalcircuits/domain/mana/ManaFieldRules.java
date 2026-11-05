package dev.elementalcircuits.domain.mana;

import dev.elementalcircuits.domain.Fixed;

/** Frozen world field rules, in 16.16 quantities and integer server ticks. */
public record ManaFieldRules(
        int version,
        int cellSize,
        int stepTicks,
        long capacity,
        long ambient,
        int diffusionDivisor,
        int decayDivisor,
        long intakePerTick,
        long crystal,
        long naturalCapacity,
        long artificialCapacity,
        long naturalPerStep,
        long artificialPerStep) {
    public ManaFieldRules {
        if (version != 1
                || cellSize != 8
                || stepTicks < 1
                || stepTicks > 20
                || diffusionDivisor < 6
                || diffusionDivisor > 1024
                || decayDivisor < 64
                || decayDivisor > 65536)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_field_rules.001"));
        for (long v :
                new long[] {
                    capacity,
                    ambient,
                    intakePerTick,
                    crystal,
                    naturalCapacity,
                    artificialCapacity,
                    naturalPerStep,
                    artificialPerStep
                }) Fixed.checked(v);
        if (capacity == 0
                || ambient > capacity
                || intakePerTick == 0
                || intakePerTick > capacity
                || crystal == 0
                || crystal > artificialCapacity
                || naturalCapacity == 0
                || naturalPerStep == 0
                || artificialPerStep == 0
                || naturalPerStep > capacity
                || artificialPerStep > capacity)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_field_rules.002"));
    }
}
