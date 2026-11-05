package dev.elementalcircuits.domain.circuit;

import dev.elementalcircuits.domain.Fixed;

/** Frozen reference: 5 runes, 4 unit links, depth 4, neutral player/wand. */
public record StandardState(
        int version,
        long transmissionLossScale,
        long storageLoss,
        int delayTicks,
        int maximumDelay,
        long nodeWeight,
        long lengthWeight,
        long depthWeight,
        long feedbackWeight,
        long referenceComplexity,
        int range,
        int radius,
        int aim) {
    public StandardState(
            int version,
            long transmissionLossScale,
            long storageLoss,
            int delayTicks,
            int maximumDelay,
            long nodeWeight,
            long lengthWeight,
            long depthWeight,
            long feedbackWeight,
            long referenceComplexity) {
        this(
                version,
                transmissionLossScale,
                storageLoss,
                delayTicks,
                maximumDelay,
                nodeWeight,
                lengthWeight,
                depthWeight,
                feedbackWeight,
                referenceComplexity,
                12,
                2,
                8192);
    }

    public static final StandardState LEGACY =
            new StandardState(
                    0, Fixed.ONE, 0, 0, 1200, Fixed.ONE, 6554, 32768, 2 * Fixed.ONE, 484968);

    public StandardState {
        if (range < 1
                || range > CircuitBounds.RANGE
                || radius < 1
                || radius > CircuitBounds.RADIUS
                || aim < 0
                || aim > Fixed.ONE
                || version < 0
                || version > 1
                || transmissionLossScale < 0
                || transmissionLossScale > Fixed.ONE
                || storageLoss < 0
                || storageLoss > Fixed.ONE
                || delayTicks < 0
                || delayTicks > 1200
                || maximumDelay < delayTicks
                || maximumDelay > 1200
                || nodeWeight < 0
                || nodeWeight > 16 * Fixed.ONE
                || lengthWeight < 0
                || lengthWeight > 16 * Fixed.ONE
                || depthWeight < 0
                || depthWeight > 16 * Fixed.ONE
                || feedbackWeight < 0
                || feedbackWeight > 16 * Fixed.ONE
                || referenceComplexity < 1
                || referenceComplexity > Fixed.MAX)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._standard_state.001"));
    }

    public static StandardState defaults() {
        return new StandardState(
                1, 6554, 3, 2, 1200, Fixed.ONE, 6554, 32768, 2 * Fixed.ONE, 484968);
    }

    public long retention(long historical) {
        return Fixed.ONE - (Fixed.ONE - historical) * transmissionLossScale / Fixed.ONE;
    }

    public int delay(CircuitComplexity complexity, long speed) {
        if (version == 0 || delayTicks == 0 || complexity.score() == 0) return 0;
        var n =
                java.math.BigInteger.valueOf(delayTicks)
                        .multiply(java.math.BigInteger.valueOf(complexity.score()))
                        .multiply(java.math.BigInteger.valueOf(Fixed.ONE));
        var d =
                java.math.BigInteger.valueOf(referenceComplexity)
                        .multiply(java.math.BigInteger.valueOf(speed));
        return n.add(d)
                .subtract(java.math.BigInteger.ONE)
                .divide(d)
                .min(java.math.BigInteger.valueOf(maximumDelay))
                .intValueExact();
    }
}
