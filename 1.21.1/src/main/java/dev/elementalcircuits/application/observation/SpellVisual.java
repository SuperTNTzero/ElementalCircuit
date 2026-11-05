package dev.elementalcircuits.application.observation;

import dev.elementalcircuits.domain.circuit.CircuitBounds;
import dev.elementalcircuits.domain.combat.Element;

import java.util.Objects;

/**
 * A disposable presentation observation. Contains no authority, damage, resources or entity access.
 */
public record SpellVisual(
        Kind kind,
        Element element,
        Point from,
        Point to,
        float size,
        int intensity,
        float aperture) {
    public SpellVisual(
            Kind kind, Element element, Point from, Point to, float size, int intensity) {
        this(kind, element, from, to, size, intensity, 0);
    }

    public enum Kind {
        BEAM,
        TRAIL,
        IMPACT,
        SHIELD,
        STEAM,
        START,
        CAST,
        FAN,
        WALL,
        RING,
        COLUMN,
        CROSS,
        DOME
    }

    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x)
                    || !Double.isFinite(y)
                    || !Double.isFinite(z)
                    || Math.abs(x) > 30000100
                    || Math.abs(y) > 30000100
                    || Math.abs(z) > 30000100)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.application.observation._spell_visual.001"));
        }

        public double distanceSquared(Point p) {
            double a = x - p.x, b = y - p.y, c = z - p.z;
            return a * a + b * b + c * c;
        }
    }

    private static boolean volume(Kind kind) {
        return switch (kind) {
            case FAN, WALL, RING, COLUMN, CROSS, DOME -> true;
            default -> false;
        };
    }

    public SpellVisual {
        if (!Float.isFinite(aperture) || aperture < 0 || aperture > 180)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.application.observation._spell_visual.002"));
        Objects.requireNonNull(kind);
        Objects.requireNonNull(element);
        Objects.requireNonNull(from);
        Objects.requireNonNull(to);
        if (!Float.isFinite(size)
                || size <= 0
                || size > CircuitBounds.RADIUS
                || intensity < 1
                || intensity > 32)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.application.observation._spell_visual.003"));
        if (volume(kind) && size != Math.rint(size))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.application.observation._spell_visual.004"));
        if ((kind == Kind.BEAM || kind == Kind.TRAIL)
                && from.distanceSquared(to) > (CircuitBounds.RANGE + 1) * (CircuitBounds.RANGE + 1))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.application.observation._spell_visual.005"));
        if ((kind == Kind.SHIELD || kind == Kind.START || kind == Kind.CAST || volume(kind))
                && Math.abs(to.x * to.x + to.y * to.y + to.z * to.z - 1) > .001)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.application.observation._spell_visual.006"));
    }

    /** Segment distance, not midpoint distance: nearby endpoints of a long beam remain visible. */
    public boolean near(Point p, double range) {
        if (kind != Kind.BEAM && kind != Kind.TRAIL) {
            double extent = kind == Kind.FAN ? size * 3 : volume(kind) ? size * 1.75 : size;
            return from.distanceSquared(p) <= (range + extent) * (range + extent);
        }
        double x = to.x - from.x, y = to.y - from.y, z = to.z - from.z, len = x * x + y * y + z * z;
        double t =
                len == 0
                        ? 0
                        : Math.max(
                                0,
                                Math.min(
                                        1,
                                        ((p.x - from.x) * x
                                                        + (p.y - from.y) * y
                                                        + (p.z - from.z) * z)
                                                / len));
        return new Point(from.x + x * t, from.y + y * t, from.z + z * t).distanceSquared(p)
                <= range * range;
    }
}
