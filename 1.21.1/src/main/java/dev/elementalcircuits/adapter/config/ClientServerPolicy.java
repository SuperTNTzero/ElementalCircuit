package dev.elementalcircuits.adapter.config;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.casting.CastGesture;

/** Display cache only. Never used for consumption or progression. */
public final class ClientServerPolicy {
    private static long fruit, maximum;
    private static int chargeStart = 6, chargeFull = 40;
    private static long chargeMaximum = 2 * Fixed.ONE;

    public static void charge(int start, int full, long peak) {
        new CastGesture(start, full, peak);
        chargeStart = start;
        chargeFull = full;
        chargeMaximum = peak;
    }

    public static int chargeStart() {
        return chargeStart;
    }

    public static int chargeFull() {
        return chargeFull;
    }

    public static long chargeMaximum() {
        return chargeMaximum;
    }

    public static void accept(long increment, long limit) {
        Fixed.checked(increment);
        Fixed.checked(limit);
        if (increment == 0 || increment > limit)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.config._client_server_policy.001"));
        fruit = increment;
        maximum = limit;
    }

    public static long fruit() {
        return fruit == 0 ? ServerSettings.growthRules().fruitIncrease() : fruit;
    }

    public static long maximum() {
        return maximum == 0 ? ServerSettings.growthRules().maximum() : maximum;
    }

    public static void clear() {
        fruit = maximum = 0;
        chargeStart = 6;
        chargeFull = 40;
        chargeMaximum = 2 * Fixed.ONE;
    }

    private ClientServerPolicy() {}
}
