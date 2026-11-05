package dev.elementalcircuits.domain.casting;

import dev.elementalcircuits.domain.Fixed;

/** Independent effects carried by mana; a connection alone grants no world operation. */
public record ConnectionEffects(
        long force,
        long constraint,
        boolean interact,
        boolean high,
        boolean anchored,
        int target,
        int control) {
    public ConnectionEffects(long force, long constraint, boolean interact, boolean high) {
        this(force, constraint, interact, high, false, 0, 0);
    }

    public static final ConnectionEffects NONE = new ConnectionEffects(0, 0, false, false);

    public ConnectionEffects {
        Fixed.checked(force);
        Fixed.checked(constraint);
        if (high && !interact)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._connection_effects.001"));
        if (target < 0 || target > 3 || control < 0)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._connection_effects.002"));
    }

    public boolean empty() {
        return force == 0
                && constraint == 0
                && !interact
                && !anchored
                && target == 0
                && control == 0;
    }

    public ConnectionEffects withSignal(boolean value) {
        return new ConnectionEffects(
                force, constraint, interact, interact && value, anchored, target, control);
    }

    public ConnectionEffects merge(ConnectionEffects b) {
        return new ConnectionEffects(
                combine(force, b.force),
                combine(constraint, b.constraint),
                interact || b.interact,
                high || b.high,
                anchored || b.anchored,
                (int) combine(target, b.target),
                (int) combine(control, b.control));
    }

    private static long combine(long a, long b) {
        if (a == 0) return b;
        if (b == 0 || a == b) return a;
        throw new IllegalArgumentException(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.casting._connection_effects.003"));
    }
}
