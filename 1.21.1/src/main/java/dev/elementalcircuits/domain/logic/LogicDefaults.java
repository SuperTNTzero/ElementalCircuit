package dev.elementalcircuits.domain.logic;

/** Creation defaults only; chosen parameters are stored in each rune's blueprint. */
public record LogicDefaults(int version, int counterThreshold, int clockPeriod) {
    public static final int MAX_COUNT = Integer.MAX_VALUE - 1, MAX_PERIOD = Integer.MAX_VALUE - 1;

    public LogicDefaults {
        if (version != 1
                || counterThreshold < 1
                || counterThreshold > MAX_COUNT
                || clockPeriod < 2
                || clockPeriod > MAX_PERIOD)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.logic._logic_defaults.001"));
    }

    public static LogicDefaults protocolV1() {
        return new LogicDefaults(1, 4, 20);
    }
}
