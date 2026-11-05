package dev.elementalcircuits.domain.casting;

import dev.elementalcircuits.domain.Fixed;

/** Prepaid world allowance. Failed payment never applies a partial action or creates energy. */
public final class ConnectionBudget {
    private long remaining;
    private boolean high;

    public ConnectionBudget(long remaining, boolean high) {
        this.remaining = Fixed.checked(remaining);
        this.high = high;
    }

    public long remaining() {
        return remaining;
    }

    public boolean high() {
        return high;
    }

    public void add(long amount) {
        Fixed.checked(amount);
        remaining += Math.min(amount, Fixed.MAX - remaining);
    }

    public boolean pay(long amount) {
        if (amount < 1 || amount > remaining) return false;
        remaining -= amount;
        return true;
    }

    /** The sampled level belongs to this output's retained target, not to rendering frames. */
    public boolean edge(boolean value) {
        boolean edge = value && !high;
        high = value;
        return edge;
    }

    public static long cost(double value) {
        if (!Double.isFinite(value) || value < 0)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._connection_budget.001"));
        return (long) Math.max(1, Math.min(Long.MAX_VALUE, Math.ceil(value * Fixed.ONE)));
    }
}
