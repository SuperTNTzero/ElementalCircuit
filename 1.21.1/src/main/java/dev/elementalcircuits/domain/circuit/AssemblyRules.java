package dev.elementalcircuits.domain.circuit;

import dev.elementalcircuits.domain.Fixed;

/** Frozen carrier/network limits; adding a slot never creates mana. */
public record AssemblyRules(
        int version,
        int maxSlots,
        int maxLinks,
        long retention,
        int externalSlots,
        int range,
        long blockCapacity,
        long chargeStep) {
    public AssemblyRules {
        if (version != 1
                || maxSlots < 1
                || maxSlots > 4
                || maxLinks < 1
                || maxLinks > 32
                || retention < 1
                || retention > Fixed.ONE
                || externalSlots < 1
                || externalSlots > maxSlots
                || range < 1
                || range > 32
                || blockCapacity < Fixed.ONE
                || blockCapacity > 256 * Fixed.ONE
                || chargeStep < 1
                || chargeStep > blockCapacity)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._assembly_rules.001"));
    }
}
