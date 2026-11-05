package dev.elementalcircuits.domain.combat;

import dev.elementalcircuits.domain.Fixed;

import java.util.*;

/** Frozen combat tuning; all mana-derived numbers use 16.16 arithmetic. */
public record ElementalCombatRules(
        int version,
        Map<DamageAffinity, Long> baseDamage,
        long maxDamage,
        long threshold,
        Multipliers multipliers,
        Status status,
        ElementReactionRules reactions) {
    public ElementalCombatRules(
            int version,
            Map<DamageAffinity, Long> baseDamage,
            long maxDamage,
            long threshold,
            Multipliers multipliers,
            Status status) {
        this(
                version,
                baseDamage,
                maxDamage,
                threshold,
                multipliers,
                status,
                ElementReactionRules.standard());
    }

    public record Multipliers(
            long wetFire, long wetLightning, long wetFrost, long shatter, long undeadLight) {
        public Multipliers {
            for (long v : new long[] {wetFire, wetLightning, wetFrost, shatter, undeadLight})
                if (v < 0 || v > 8 * Fixed.ONE)
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.domain.combat._elemental_combat_rules.001"));
        }
    }

    public record Status(
            int wetTicks,
            int freezePerMana,
            int thawPerMana,
            int maxFrozen,
            int freezeThreshold,
            int fireMaxSeconds,
            int fanTicks,
            int slowPerMana,
            int slowMaxTicks,
            int earthSlowPerMana,
            int earthSlowMaxTicks,
            int blindPerMana,
            int blindMaxTicks,
            long impulsePerMana,
            long maxImpulse) {
        public Status {
            for (int v :
                    new int[] {
                        wetTicks,
                        freezePerMana,
                        thawPerMana,
                        maxFrozen,
                        freezeThreshold,
                        fireMaxSeconds,
                        fanTicks,
                        slowPerMana,
                        slowMaxTicks,
                        earthSlowPerMana,
                        earthSlowMaxTicks,
                        blindPerMana,
                        blindMaxTicks
                    })
                if (v < 1 || v > 12000)
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.domain.combat._elemental_combat_rules.002"));
            if (freezeThreshold > maxFrozen
                    || impulsePerMana < 0
                    || impulsePerMana > Fixed.ONE
                    || maxImpulse < 0
                    || maxImpulse > 2 * Fixed.ONE)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.combat._elemental_combat_rules.003"));
        }
    }

    public ElementalCombatRules {
        if (version != 1
                || baseDamage == null
                || baseDamage.size() != DamageAffinity.values().length
                || maxDamage < Fixed.ONE
                || maxDamage > 1024 * Fixed.ONE
                || threshold < 1
                || threshold > Fixed.ONE
                || multipliers == null
                || status == null
                || reactions == null)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.combat._elemental_combat_rules.004"));
        baseDamage = Map.copyOf(baseDamage);
        for (var a : DamageAffinity.values()) {
            Long rate = baseDamage.get(a);
            if (rate == null || rate < 0 || rate > 8 * Fixed.ONE)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.combat._elemental_combat_rules.005"));
        }
        if (baseDamage.get(DamageAffinity.PURE) != 0 || baseDamage.get(DamageAffinity.MIXED) != 0)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.combat._elemental_combat_rules.006"));
    }

    public long damage(DamageAffinity a, long mana) {
        return mana * baseDamage.get(a) / Fixed.ONE;
    }
}
