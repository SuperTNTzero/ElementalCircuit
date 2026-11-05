package dev.elementalcircuits.domain.casting;

import dev.elementalcircuits.domain.Fixed;

/** Native item-use gesture. Charging never funds or advances a circuit; release is handled once. */
public final class CastGesture {
    public enum Action {
        NONE,
        CHARGE_STARTED,
        RELEASE
    }

    private final int threshold, full;
    private final long maximum;
    private int elapsed;
    private boolean charging, closed;

    public CastGesture(int threshold, int full, long maximum) {
        if (threshold < 2
                || threshold > 40
                || full <= threshold
                || full > 200
                || maximum < Fixed.ONE
                || maximum > 2 * Fixed.ONE)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._cast_gesture.001"));
        this.threshold = threshold;
        this.full = full;
        this.maximum = maximum;
    }

    public Action advance(int ticks) {
        if (ticks < elapsed || ticks < 0)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._cast_gesture.002"));
        elapsed = ticks;
        if (closed || charging || ticks < threshold) return Action.NONE;
        charging = true;
        return Action.CHARGE_STARTED;
    }

    public long multiplier() {
        return elapsed <= threshold
                ? Fixed.ONE
                : Fixed.ONE
                        + (maximum - Fixed.ONE)
                                * Math.min(full - threshold, elapsed - threshold)
                                / (full - threshold);
    }

    public Action release() {
        if (closed) return Action.NONE;
        closed = true;
        return Action.RELEASE;
    }

    public void cancel() {
        closed = true;
    }
}
