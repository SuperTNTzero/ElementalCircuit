package dev.elementalcircuits.domain.casting;

import dev.elementalcircuits.domain.Fixed;

/**
 * Angles in degrees (16.16); distance in blocks. Zero aperture/distance uses historical defaults.
 */
public record SpatialParameters(long angle, long yaw, long pitch, long distance) {
    public static final SpatialParameters NONE = new SpatialParameters(0, 0, 0, 0);

    public SpatialParameters {
        if (angle < 0
                || angle > 180 * Fixed.ONE
                || yaw < -180 * Fixed.ONE
                || yaw > 180 * Fixed.ONE
                || pitch < -90 * Fixed.ONE
                || pitch > 90 * Fixed.ONE
                || distance < 0
                || distance > 256 * Fixed.ONE)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._spatial_parameters.001"));
    }

    public SpatialParameters merge(SpatialParameters b) {
        return new SpatialParameters(
                combine(angle, b.angle),
                combine(yaw, b.yaw),
                combine(pitch, b.pitch),
                combine(distance, b.distance));
    }

    private static long combine(long a, long b) {
        if (a == 0) return b;
        if (b == 0 || a == b) return a;
        throw new IllegalArgumentException(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.casting._spatial_parameters.002"));
    }

    public double aperture(double fallback) {
        return angle == 0 ? fallback : angle / (double) Fixed.ONE;
    }

    /**
     * Orthonormal local rotation, independent of Minecraft or visual geometry. Positive pitch aims
     * up.
     */
    public double[] direction(double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (!Double.isFinite(length) || length < .000001)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._spatial_parameters.003"));
        x /= length;
        y /= length;
        z /= length;
        double a = Math.toRadians(yaw / (double) Fixed.ONE);
        double px = x * Math.cos(a) - z * Math.sin(a), pz = x * Math.sin(a) + z * Math.cos(a);
        double horizontal = Math.hypot(px, pz);
        double elevation =
                Math.asin(Math.max(-1, Math.min(1, y)))
                        + Math.toRadians(pitch / (double) Fixed.ONE);
        if (horizontal < .000001) {
            px = -Math.sin(a);
            pz = Math.cos(a);
            horizontal = 1;
        }
        return new double[] {
            px / horizontal * Math.cos(elevation),
            Math.sin(elevation),
            pz / horizontal * Math.cos(elevation)
        };
    }
}
