package dev.elementalcircuits.domain.mana;

import dev.elementalcircuits.domain.Fixed;

/** Temporary additive per-level multiplier, applied to the player's configured base rate. */
public record ManaRecoveryRules(
        int version, long bonusPerLevel, int duration, int longDuration, int strongDuration) {
    public ManaRecoveryRules {
        if (version != 1
                || bonusPerLevel < 0
                || bonusPerLevel > 16 * Fixed.ONE
                || duration < 1
                || longDuration < duration
                || strongDuration < 1
                || longDuration > 72000
                || strongDuration > 72000)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_recovery_rules.001"));
    }

    public long rate(long base, int amplifier) {
        Fixed.checked(base);
        if (amplifier < 0 || amplifier > 255)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_recovery_rules.002"));
        long multiplier = Fixed.ONE + bonusPerLevel * (amplifier + 1L);
        return Math.min(Fixed.MAX, Math.multiplyExact(base, multiplier) / Fixed.ONE);
    }

    public ManaRecoveryRules withBonus(long bonus) {
        return new ManaRecoveryRules(version, bonus, duration, longDuration, strongDuration);
    }
}
