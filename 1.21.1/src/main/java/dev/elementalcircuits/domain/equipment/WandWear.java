package dev.elementalcircuits.domain.equipment;

import dev.elementalcircuits.domain.Fixed;

/** Piecewise over-stress curve. Below both material thresholds, a release costs one durability. */
public final class WandWear {
    private WandWear() {}

    public static int cost(
            long effect,
            long strength,
            int intervalTicks,
            double safeIntensity,
            double safeFrequency,
            double intensityCoefficient,
            double speedCoefficient) {
        Fixed.checked(effect);
        if (strength < Fixed.ONE / 4
                || strength > 2 * Fixed.ONE
                || intervalTicks < 1
                || safeIntensity <= 0
                || safeFrequency <= 0
                || !Double.isFinite(
                        safeIntensity + safeFrequency + intensityCoefficient + speedCoefficient)
                || intensityCoefficient < 0
                || speedCoefficient < 0)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.equipment._wand_wear.001"));
        double intensity = effect / (double) Fixed.ONE * strength / Fixed.ONE,
                frequency = 20.0 / intervalTicks;
        double i = Math.max(0, intensity / safeIntensity - 1),
                f = Math.max(0, frequency / safeFrequency - 1);
        return (int)
                Math.min(
                        Integer.MAX_VALUE,
                        1 + Math.ceil(intensityCoefficient * i * i + speedCoefficient * f * f));
    }
}
