package dev.elementalcircuits.domain.casting;

import dev.elementalcircuits.domain.Fixed;

/** Invocation-bound player coefficients; ambient/secondary circuits default to neutral. */
public record CastContext(
        long damageMultiplier, long manaMultiplier, boolean reactive, long speed) {
    public CastContext(long damageMultiplier, long manaMultiplier, boolean reactive) {
        this(damageMultiplier, manaMultiplier, reactive, Fixed.ONE);
    }

    public CastContext charged(long scale) {
        if (scale < Fixed.ONE || scale > 2 * Fixed.ONE)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._cast_context.001"));
        long damage =
                Math.min(
                        Math.min(16 * Fixed.ONE, Fixed.MAX * damageMultiplier / manaMultiplier),
                        damageMultiplier * scale / Fixed.ONE);
        long cost = Math.min(Fixed.MAX, manaMultiplier * damage / damageMultiplier);
        return new CastContext(damage, Math.max(1, cost), reactive, speed);
    }

    public CastContext withSpeed(long value) {
        return new CastContext(damageMultiplier, manaMultiplier, reactive, value);
    }

    public CastContext(long damageMultiplier, long manaMultiplier) {
        this(damageMultiplier, manaMultiplier, false);
    }

    public CastContext withReactive(boolean value) {
        return new CastContext(damageMultiplier, manaMultiplier, value, speed);
    }

    public static final CastContext NEUTRAL = new CastContext(Fixed.ONE, Fixed.ONE);

    public CastContext {
        if (speed < Fixed.ONE / 4
                || speed > 2 * Fixed.ONE
                || damageMultiplier < Fixed.ONE
                || damageMultiplier > 16 * Fixed.ONE
                || manaMultiplier < 1
                || manaMultiplier > Fixed.MAX)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._cast_context.002"));
    }
}
