package dev.elementalcircuits.domain.casting;

import dev.elementalcircuits.domain.Fixed;

/** Logical grid footprint, independent of target density, chunk state and rendering. O(r^2). */
public final class ShapeFootprint {
    private ShapeFootprint() {}

    private record Key(
            SpellSpec.Shape shape, int radius, int range, ShapeRules rules, long angle) {}

    private static final java.util.concurrent.ConcurrentHashMap<Key, Long> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static long blocks(SpellSpec spec, ShapeRules rules) {
        if (spec.method() == SpellSpec.Method.CONTACT) return 0;
        var key = new Key(spec.shape(), spec.radius(), spec.range(), rules, spec.spatial().angle());
        var known = CACHE.get(key);
        if (known != null) return known;
        long n = count(spec, rules);
        if (CACHE.size() >= 4096) CACHE.clear();
        CACHE.put(key, n);
        return n;
    }

    private static long count(SpellSpec spec, ShapeRules rules) {
        int r = spec.radius(), range = spec.range();
        var shape = spec.shape();
        if (shape == SpellSpec.Shape.BEAM) return Math.max(1, range);
        if (shape == SpellSpec.Shape.WALL)
            return (2L * r + 1) * (2L * r + 1) * (2 * (rules.wallThickness() / Fixed.ONE) + 1);
        long count = 0;
        int reach =
                shape == SpellSpec.Shape.FAN
                        ? (int) Math.ceil(r * rules.fanReach() / (double) Fixed.ONE)
                        : r;
        ShapeVolume volume =
                ShapeVolume.shaped(shape)
                        ? new ShapeVolume(
                                shape, r, rules, spec.spatial().aperture(rules.fanDegrees()))
                        : null;
        for (int x = -reach; x <= reach; x++)
            for (int z = shape == SpellSpec.Shape.FAN ? 0 : -reach; z <= reach; z++) {
                long horizontal = (long) x * x + (long) z * z;
                if (shape == SpellSpec.Shape.SHIELD) {
                    if (horizontal <= (long) r * r) count++;
                    continue;
                }
                if (volume == null) {
                    if (horizontal <= (long) r * r)
                        count += 2 * (long) Math.floor(Math.sqrt((long) r * r - horizontal)) + 1;
                    continue;
                }
                if (shape == SpellSpec.Shape.COLUMN) {
                    if (volume.contains(x, 0, z)) count += 2L * r + 1;
                } else if (shape == SpellSpec.Shape.DOME) {
                    long outer = (long) r * r - horizontal;
                    if (outer < 0) continue;
                    int hi = (int) Math.floor(Math.sqrt(outer));
                    double inner = r * rules.domeInner() / (double) Fixed.ONE;
                    int lo = (int) Math.ceil(Math.sqrt(Math.max(0, inner * inner - horizontal)));
                    count += Math.max(0, hi - lo + 1);
                } else if (shape == SpellSpec.Shape.FAN) {
                    if (volume.contains(x, 0, z))
                        count +=
                                2
                                                * (long)
                                                        Math.floor(
                                                                Math.max(
                                                                        .2,
                                                                        r
                                                                                * rules.fanHeight()
                                                                                / (double)
                                                                                        Fixed.ONE))
                                        + 1;
                } else if (volume.contains(x, 0, z))
                    count += 2L * (rules.slabHeight() / Fixed.ONE) + 1;
            }
        return Math.max(1, count);
    }
}
