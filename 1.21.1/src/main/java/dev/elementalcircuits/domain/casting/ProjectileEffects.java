package dev.elementalcircuits.domain.casting;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.circuit.CircuitBounds;
import dev.elementalcircuits.domain.circuit.Rune;

/** Independent flight, hit and secondary requests; immutable and bounded. */
public record ProjectileEffects(
        int burst,
        int bounces,
        int homing,
        int fragments,
        int chains,
        int ignite,
        int slow,
        int knockback) {
    public ProjectileEffects(int burst, int bounces, int homing) {
        this(burst, bounces, homing, 0, 0, 0, 0, 0);
    }

    public static final ProjectileEffects NONE = new ProjectileEffects(0, 0, 0);

    public ProjectileEffects {
        if (burst < 0
                || burst > CircuitBounds.RADIUS
                || bounces < 0
                || bounces > CircuitBounds.DERIVATIVES
                || homing < 0
                || homing > CircuitBounds.RANGE
                || homing == 1
                || fragments < 0
                || fragments > CircuitBounds.DERIVATIVES
                || fragments == 1
                || chains < 0
                || chains > CircuitBounds.DERIVATIVES
                || ignite < 0
                || ignite > CircuitBounds.DURATION
                || slow < 0
                || slow > CircuitBounds.DURATION
                || knockback < 0
                || knockback > CircuitBounds.DERIVATIVES)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._projectile_effects.001"));
    }

    private static int combine(int a, int b) {
        if (a == 0) return b;
        if (b == 0 || a == b) return a;
        throw new IllegalArgumentException(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.casting._projectile_effects.002"));
    }

    public ProjectileEffects merge(ProjectileEffects b) {
        return new ProjectileEffects(
                combine(burst, b.burst),
                combine(bounces, b.bounces),
                combine(homing, b.homing),
                combine(fragments, b.fragments),
                combine(chains, b.chains),
                combine(ignite, b.ignite),
                combine(slow, b.slow),
                combine(knockback, b.knockback));
    }

    public boolean empty() {
        return equals(NONE);
    }

    public boolean secondary() {
        return fragments > 0 || chains > 0;
    }

    public boolean hit() {
        return ignite > 0 || slow > 0 || knockback > 0;
    }

    public static boolean rune(Rune.Kind k) {
        return k == Rune.Kind.BURST
                || k == Rune.Kind.BOUNCE
                || k == Rune.Kind.HOMING
                || k == Rune.Kind.FRAGMENT
                || k == Rune.Kind.CHAIN
                || k == Rune.Kind.IGNITE
                || k == Rune.Kind.SLOW
                || k == Rune.Kind.KNOCKBACK;
    }

    public static ProjectileEffects from(Rune r) {
        int n = (int) (r.value() / Fixed.ONE);
        return switch (r.kind()) {
            case BURST -> new ProjectileEffects(n, 0, 0);
            case BOUNCE -> new ProjectileEffects(0, n, 0);
            case HOMING -> new ProjectileEffects(0, 0, n);
            case FRAGMENT -> new ProjectileEffects(0, 0, 0, n, 0, 0, 0, 0);
            case CHAIN -> new ProjectileEffects(0, 0, 0, 0, n, 0, 0, 0);
            case IGNITE -> new ProjectileEffects(0, 0, 0, 0, 0, n, 0, 0);
            case SLOW -> new ProjectileEffects(0, 0, 0, 0, 0, 0, n, 0);
            case KNOCKBACK -> new ProjectileEffects(0, 0, 0, 0, 0, 0, 0, n);
            default -> NONE;
        };
    }

    public int impactRadius(int fallback) {
        return burst == 0 ? fallback : burst;
    }
}
