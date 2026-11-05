package dev.elementalcircuits.domain.casting;

import dev.elementalcircuits.domain.Fixed;

/** Versioned game-action thresholds, not joules, mass or a thermal simulation. */
public record WorldInteractionRules(
        int version,
        long blockCost,
        long sourceCost,
        long impulsePerUnit,
        long maxImpulse,
        int maxTargets,
        int frostedIceDelay) {
    public WorldInteractionRules {
        if (version != 1
                || blockCost < 1
                || sourceCost < blockCost
                || sourceCost > 64 * Fixed.ONE
                || impulsePerUnit < 1
                || impulsePerUnit > Fixed.ONE
                || maxImpulse < 1
                || maxImpulse > 2 * Fixed.ONE
                || maxTargets < 1
                || maxTargets > 128
                || frostedIceDelay < 1
                || frostedIceDelay > 1200)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._world_interaction_rules.001"));
    }

    public long required(long cost, long strength) {
        return (cost * Fixed.ONE + strength - 1) / strength;
    }
}
