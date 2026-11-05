package dev.elementalcircuits.client.effect;

import dev.elementalcircuits.adapter.resources.ShapeResources;
import dev.elementalcircuits.application.observation.*;
import dev.elementalcircuits.domain.casting.ShapeVolume;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.combat.Element;

import java.util.List;

/** Pure cosmetic geometry. No world, entity, RNG, collision or graphics API dependency. */
public final class SpellGeometry {
    public static final int QUAD_LIMIT = 8192;

    public record V(double x, double y, double z) {
        public V add(V v) {
            return new V(x + v.x, y + v.y, z + v.z);
        }

        public V sub(V v) {
            return new V(x - v.x, y - v.y, z - v.z);
        }

        public V mul(double n) {
            return new V(x * n, y * n, z * n);
        }

        public double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }

        public V unit() {
            double n = length();
            return n < 1e-9 ? new V(0, 1, 0) : mul(1 / n);
        }

        public V cross(V v) {
            return new V(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x);
        }

        public static V of(SpellVisual.Point p) {
            return new V(p.x(), p.y(), p.z());
        }
    }

    @FunctionalInterface
    public interface Sink {
        void quad(V a, V b, V c, V d, int sprite, int color, int alpha);
    }

    private final V camera, right, up;
    private final Sink sink;
    private int count;

    private SpellGeometry(V camera, V right, V up, Sink sink) {
        this.camera = camera;
        this.right = right;
        this.up = up;
        this.sink = sink;
    }

    public static int emit(
            List<VisualTimeline.Frame> frames, V camera, V right, V up, float partial, Sink sink) {
        var mesh = new SpellGeometry(camera, right, up, sink);
        var observer = new SpellVisual.Point(camera.x, camera.y, camera.z);
        // Contacts and cast silhouettes remain visible when trails flood the cosmetic budget.
        for (int pass = 0; pass < 2; pass++)
            for (var frame : frames) {
                if (mesh.count >= QUAD_LIMIT) return mesh.count;
                var e = frame.event();
                boolean important =
                        e.kind() == SpellVisual.Kind.IMPACT
                                || e.kind() == SpellVisual.Kind.CAST
                                || e.kind() == SpellVisual.Kind.START;
                if (important != (pass == 0) || !e.near(observer, 80)) continue;
                float p =
                        Math.min(
                                1,
                                Math.max(
                                        0,
                                        (frame.age() + Math.max(0, Math.min(1, partial)))
                                                / frame.life()));
                mesh.event(e, p, frame.animationAge() + partial);
            }
        return mesh.count;
    }

    private void quad(V a, V b, V c, V d, int sprite, int color, double opacity) {
        if (count >= QUAD_LIMIT || opacity <= .002) return;
        sink.quad(a, b, c, d, sprite, color, (int) Math.round(Math.min(1, opacity) * 255));
        count++;
    }

    private void plane(V at, V r, V u, int sprite, int color, double alpha) {
        quad(
                at.sub(r).sub(u),
                at.add(r).sub(u),
                at.add(r).add(u),
                at.sub(r).add(u),
                sprite,
                color,
                alpha);
    }

    private void billboard(V at, double size, int sprite, int color, double alpha, double angle) {
        V r = right.mul(Math.cos(angle) * size).add(up.mul(Math.sin(angle) * size)),
                u = up.mul(Math.cos(angle) * size).sub(right.mul(Math.sin(angle) * size));
        plane(at, r, u, sprite, color, alpha);
    }

    private void ribbon(V a, V b, double width, int color, double alpha) {
        V axis = b.sub(a);
        if (axis.length() < 1e-6) return;
        V side = axis.cross(a.add(b).mul(.5));
        if (side.length() < 1e-6) side = axis.cross(up);
        if (side.length() < 1e-6) side = right;
        side = side.unit().mul(width);
        quad(a.sub(side), b.sub(side), b.add(side), a.add(side), 2, color, alpha);
    }

    private void ring(
            V at, V r, V u, double size, double angle, int sprite, int color, double alpha) {
        V x = r.mul(Math.cos(angle) * size).add(u.mul(Math.sin(angle) * size)),
                y = u.mul(Math.cos(angle) * size).sub(r.mul(Math.sin(angle) * size));
        plane(at, x, y, sprite, color, alpha);
    }

    private void event(SpellVisual e, float p, float age) {
        var s = VisualStyles.of(e.kind());
        var art = VisualStyles.of(e.element());
        V a = V.of(e.from()).sub(camera), b = V.of(e.to()).sub(camera);
        double
                alpha =
                        s.opacity()
                                * (e.kind() == SpellVisual.Kind.SHIELD ? 1 : Math.pow(1 - p, 1.3)),
                size = e.size();
        switch (e.kind()) {
            case BEAM, TRAIL -> ElementalGeometry.flight(e, a, b, p, age, alpha, brush());
            case IMPACT -> impact(e, a, s, art, p, alpha);
            case START, CAST -> {
                V normal = V.of(e.to()),
                        r =
                                normal.cross(
                                                Math.abs(normal.y) > .98
                                                        ? new V(1, 0, 0)
                                                        : new V(0, 1, 0))
                                        .unit(),
                        u = r.cross(normal).unit();
                double radius =
                        s.width()
                                * size
                                * (e.kind() == SpellVisual.Kind.START ? .65 + .5 * p : 1 + .6 * p);
                if (a.length() < 1.6) {
                    radius *= .55;
                    alpha *= .65;
                }
                int glyph =
                        switch (e.element()) {
                            case FIRE, LIGHTNING -> 14;
                            case WATER, WIND -> 6;
                            case EARTH, LIGHT -> 5;
                            default -> 7;
                        };
                ring(a, r, u, radius, age * .12, glyph, art.color(), alpha);
                ring(a, r, u, radius * .73, -age * .2, 3, art.core(), alpha * .8);
                billboard(a, radius * .5, 0, art.color(), alpha * .8, 0);
                if (p < .35)
                    billboard(a, radius * .5 * (1 - p / .35), 4, art.core(), alpha, age * .05);
                for (int i = 0; i < s.copies(); i++) {
                    double angle = i * Math.PI * 2 / s.copies() + age * .1;
                    V at =
                            a.add(r.mul(Math.cos(angle) * radius * .9))
                                    .add(u.mul(Math.sin(angle) * radius * .9));
                    billboard(at, .055 * size, 12, art.core(), alpha, angle);
                }
            }
            case SHIELD -> {
                ElementalGeometry.shield(e, a, V.of(e.to()), p, age, alpha, brush());
            }
            case FAN, WALL, RING, COLUMN, CROSS, DOME -> {
                V n = V.of(e.to());
                var volume =
                        new ShapeVolume(
                                SpellSpec.Shape.valueOf(e.kind().name()),
                                Math.round(e.size()),
                                ShapeResources.rules(),
                                e.aperture() > 0
                                        ? e.aperture()
                                        : ShapeResources.rules().fanDegrees());
                ElementalGeometry.volume(e, volume, a, n, p, age, alpha, brush());
            }
            case STEAM -> {
                for (int i = 0; i < s.copies(); i++) {
                    double angle = i * 2.39996;
                    V at =
                            a.add(right.mul(Math.cos(angle) * (.15 + p * .5)))
                                    .add(up.mul(.1 + i * .06 + p * .8));
                    billboard(
                            at,
                            s.width() * (1 + p) * (.8 + i * .07),
                            1,
                            0xcbdde5,
                            alpha * .5,
                            angle * .1);
                }
            }
        }
    }

    private ElementalGeometry.Brush brush() {
        return new ElementalGeometry.Brush() {
            public void sprite(
                    V at, double size, int tile, int color, double alpha, double rotation) {
                billboard(at, size, tile, color, alpha, rotation);
            }

            public void line(V a, V b, double width, int color, double alpha) {
                ribbon(a, b, width, color, alpha);
            }

            public void face(V at, V r, V u, int tile, int color, double alpha) {
                plane(at, r, u, tile, color, alpha);
            }
        };
    }

    private void impact(
            SpellVisual e,
            V a,
            VisualStyles.Style s,
            VisualStyles.ElementStyle art,
            float p,
            double alpha) {
        double size = Math.min(2, e.size()) * (.8 + Math.sqrt(e.intensity()) * .06),
                expansion = 1 - Math.pow(1 - p, 3),
                radius = size * art.spread() * (.15 + expansion * 1.6);
        billboard(a, radius, 3, art.color(), alpha * .85, p * .2);
        billboard(
                a,
                size * (e.element() == Element.SHADOW ? 1.35 - p * .8 : .6 + expansion * .7),
                SpellAtlas.frame(e.element(), p * 12),
                SpellAtlas.colored(e.element()) ? 0xffffff : art.color(),
                alpha * .9,
                e.element() == Element.SHADOW ? -p * 2 : p * .3);
        billboard(
                a,
                radius * .63,
                e.element() == Element.ARCANE ? 7 : e.element() == Element.WIND ? 11 : 3,
                art.core(),
                alpha * .55,
                -p * 1.5);
        if (p < .4) {
            double burst = 1 - p / .4;
            billboard(a, size * (.5 + expansion) * burst, 0, art.color(), burst * .8, 0);
            billboard(a, size * .52 * burst, 4, art.core(), burst, 0);
        }
        if (e.element() == Element.SHADOW)
            billboard(a, size * (.7 - p * .5), 0, 0x160c25, alpha * .95, 0);
        int n = Math.min(s.copies(), art.particles());
        for (int i = 0; i < n; i++) {
            double angle = i * 2.399963 + e.from().x() * .37 + e.from().z() * .17,
                    spread = (.35 + (i % 5) * .13) * radius;
            if (e.element() == Element.SHADOW) spread = (1 - p) * size * 1.4;
            double turn = angle + p * art.twist(),
                    dx = Math.cos(turn) * spread,
                    dy = Math.sin(turn) * spread;
            V at = a.add(right.mul(dx)).add(up.mul(dy)).add(new V(0, art.rise() * p * p * size, 0));
            double particleSize = s.width() * size * (.6 + (i % 3) * .2) * (1 - p * .65);
            billboard(
                    at,
                    particleSize,
                    art.sprite(),
                    i % 3 == 0 ? art.core() : art.color(),
                    alpha,
                    turn);
            if (i % 2 == 0) {
                V tail =
                        at.sub(right.mul(Math.cos(turn) * .24 * size))
                                .sub(up.mul(Math.sin(turn) * .24 * size));
                ribbon(tail, at, particleSize * .17, art.core(), alpha * .85);
            }
        }
        if (e.element() == Element.LIGHTNING) {
            for (int i = 0; i < 4; i++) {
                double angle = i * Math.PI / 2 + p * .6;
                V end =
                        a.add(right.mul(Math.cos(angle) * radius))
                                .add(up.mul(Math.sin(angle) * radius));
                V kink = a.add(end.sub(a).mul(.5)).add(up.mul(size * .13));
                ribbon(a, kink, .025 * size, art.core(), alpha);
                ribbon(kink, end, .016 * size, art.core(), alpha);
            }
        }
        if (e.element() == Element.EARTH)
            billboard(a, size * (.7 + expansion), 14, art.color(), alpha * .65, 0);
        if (e.element() == Element.FIRE)
            billboard(
                    a.add(new V(0, p * .6, 0)), size * (.4 + p * .4), 1, 0x593528, alpha * .35, 0);
    }
}
