package dev.elementalcircuits.domain.casting;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.circuit.CircuitBounds;
import dev.elementalcircuits.domain.circuit.Rune;

/** Pure local-space membership. X is right, Y up, Z forward; no world or renderer. */
public record ShapeVolume(SpellSpec.Shape shape, int radius, ShapeRules rules, double aperture) {
    public ShapeVolume(SpellSpec.Shape shape, int radius, ShapeRules rules) {
        this(shape, radius, rules, rules.fanDegrees());
    }

    public record Bounds(
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}

    public ShapeVolume {
        if (!shaped(shape)
                || radius < 1
                || radius > CircuitBounds.RADIUS
                || rules == null
                || !Double.isFinite(aperture)
                || aperture < 1
                || aperture > 180)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._shape_volume.001"));
    }

    public static boolean shaped(SpellSpec.Shape shape) {
        return shape != null
                && switch (shape) {
                    case FAN, WALL, RING, COLUMN, CROSS, DOME -> true;
                    default -> false;
                };
    }

    public static boolean rune(Rune.Kind kind) {
        return switch (kind) {
            case FAN, WALL, RING, COLUMN, CROSS, DOME -> true;
            default -> false;
        };
    }

    private double ratio(int n) {
        return n / (double) Fixed.ONE;
    }

    public double reach() {
        return radius * ratio(rules.fanReach());
    }

    public double fanWidth() {
        return reach() * Math.sin(Math.toRadians(aperture / 2.0));
    }

    public boolean contains(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        double r = radius, horizontal = x * x + z * z;
        return switch (shape) {
            case FAN ->
                    z >= 0
                            && horizontal <= reach() * reach()
                            && (horizontal < .0000001
                                    || z / Math.sqrt(horizontal) + .0000001
                                            >= Math.cos(Math.toRadians(aperture / 2.0)))
                            && Math.abs(y) <= Math.max(.2, r * ratio(rules.fanHeight()));
            case WALL ->
                    Math.abs(x) <= r
                            && Math.abs(y) <= r
                            && Math.abs(z) <= ratio(rules.wallThickness());
            case RING ->
                    horizontal <= r * r
                            && horizontal >= Math.pow(r * ratio(rules.ringInner()), 2)
                            && Math.abs(y) <= ratio(rules.slabHeight());
            case COLUMN ->
                    horizontal <= Math.pow(r * ratio(rules.columnRadius()), 2) && Math.abs(y) <= r;
            case CROSS ->
                    Math.abs(y) <= ratio(rules.slabHeight())
                            && Math.abs(x) <= r
                            && Math.abs(z) <= r
                            && (Math.abs(x) <= Math.max(.25, r * ratio(rules.crossWidth()))
                                    || Math.abs(z) <= Math.max(.25, r * ratio(rules.crossWidth())));
            case DOME ->
                    y >= 0
                            && horizontal + y * y <= r * r
                            && horizontal + y * y >= Math.pow(r * ratio(rules.domeInner()), 2);
            default -> false;
        };
    }

    public Bounds bounds() {
        double r = radius;
        return switch (shape) {
            case FAN ->
                    new Bounds(
                            -fanWidth(),
                            -Math.max(.2, r * ratio(rules.fanHeight())),
                            0,
                            fanWidth(),
                            Math.max(.2, r * ratio(rules.fanHeight())),
                            reach());
            case WALL ->
                    new Bounds(
                            -r,
                            -r,
                            -ratio(rules.wallThickness()),
                            r,
                            r,
                            ratio(rules.wallThickness()));
            case RING, CROSS ->
                    new Bounds(-r, -ratio(rules.slabHeight()), -r, r, ratio(rules.slabHeight()), r);
            case COLUMN ->
                    new Bounds(
                            -r * ratio(rules.columnRadius()),
                            -r,
                            -r * ratio(rules.columnRadius()),
                            r * ratio(rules.columnRadius()),
                            r,
                            r * ratio(rules.columnRadius()));
            case DOME -> new Bounds(-r, 0, -r, r, r, r);
            default -> throw new IllegalStateException();
        };
    }
}
