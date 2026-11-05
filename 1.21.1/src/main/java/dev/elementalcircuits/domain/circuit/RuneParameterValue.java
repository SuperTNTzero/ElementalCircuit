package dev.elementalcircuits.domain.circuit;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.casting.ProjectileEffects;

import java.math.*;

/** Continuous decimal UI input is quantized once at the fixed-point boundary. */
public final class RuneParameterValue {
    private RuneParameterValue() {}

    public static long parse(Rune.Kind kind, String text) {
        if (text == null || text.length() > 256)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._rune_parameter_value.001"));
        var n = new BigDecimal(text.trim());
        if (Math.abs((long) n.scale()) > 256 || n.precision() > 256)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._rune_parameter_value.002"));
        if (kind == Rune.Kind.AIM) {
            if (n.signum() < 0 || n.compareTo(BigDecimal.ONE) > 0)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._rune_parameter_value.003"));
            return n.multiply(BigDecimal.valueOf(Fixed.ONE))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        }
        if (kind == Rune.Kind.RANGE
                || kind == Rune.Kind.ITEM_INPUT
                || kind == Rune.Kind.ITEM_OUTPUT
                || kind == Rune.Kind.REDSTONE
                || kind == Rune.Kind.RADIUS
                || kind == Rune.Kind.COUNTER
                || kind == Rune.Kind.CLOCK
                || kind == Rune.Kind.DELAY
                || kind == Rune.Kind.TARGET
                || kind == Rune.Kind.PROBE
                || kind == Rune.Kind.EDGE
                || ProjectileEffects.rune(kind)) n.toBigIntegerExact();
        return n.multiply(BigDecimal.valueOf(Fixed.ONE))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
