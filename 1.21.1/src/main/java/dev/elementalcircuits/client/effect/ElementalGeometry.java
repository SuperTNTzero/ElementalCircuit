package dev.elementalcircuits.client.effect;

import static dev.elementalcircuits.client.effect.SpellGeometry.V;

import dev.elementalcircuits.application.observation.SpellVisual;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.ShapeVolume;
import dev.elementalcircuits.domain.combat.Element;

/** Element material strokes over shared shape paths; only bounded cosmetic geometry. */
final class ElementalGeometry {
    interface Brush {
        void sprite(V at, double size, int tile, int color, double alpha, double rotation);

        void line(V a, V b, double width, int color, double alpha);

        void face(V at, V right, V up, int tile, int color, double alpha);
    }

    private ElementalGeometry() {}

    private static V axisRight(V n) {
        return n.cross(Math.abs(n.y()) > .99 ? new V(1, 0, 0) : new V(0, 1, 0)).unit();
    }

    private static int color(Element e, VisualStyles.ElementStyle art) {
        return SpellAtlas.colored(e) ? 0xffffff : art.color();
    }

    private static void arc(
            Brush brush,
            V center,
            V r,
            V u,
            double radius,
            double from,
            double to,
            double width,
            int color,
            double alpha,
            int count) {
        V last = center.add(r.mul(Math.cos(from) * radius)).add(u.mul(Math.sin(from) * radius));
        for (int i = 1; i <= count; i++) {
            double angle = from + (to - from) * i / count;
            V next =
                    center.add(r.mul(Math.cos(angle) * radius))
                            .add(u.mul(Math.sin(angle) * radius));
            brush.line(last, next, width, color, alpha);
            last = next;
        }
    }

    private static void bolt(
            Brush brush,
            V from,
            V to,
            V r,
            V u,
            double width,
            int color,
            int core,
            double alpha,
            float age) {
        int steps = Math.max(3, Math.min(24, (int) (to.sub(from).length() * 3)));
        V last = from;
        for (int i = 1; i <= steps; i++) {
            double jitter =
                    i == steps
                            ? 0
                            : Math.sin(i * 19.73 + Math.floor(age) * 2.1)
                                    * Math.min(.28, width * 2.8);
            V next =
                    from.add(to.sub(from).mul(i / (double) steps))
                            .add(r.mul(jitter))
                            .add(u.mul(jitter * .57));
            brush.line(last, next, width * 2.4, color, alpha * .24);
            brush.line(last, next, width, color, alpha * .85);
            brush.line(last, next, width * .32, core, alpha);
            last = next;
        }
    }

    static void flight(SpellVisual e, V a, V b, float p, float age, double alpha, Brush brush) {
        var art = VisualStyles.of(e.element());
        double width = VisualStyles.of(e.kind()).width() * e.size();
        V axis = b.sub(a), n = axis.unit(), r = axisRight(n), u = r.cross(n).unit();
        double length = axis.length();
        if (length < .001) return;
        Element element = e.element();
        if (element == Element.LIGHTNING) width *= 1.65;
        int tile = SpellAtlas.frame(element, age), color = color(element, art);
        int count = Math.max(4, Math.min(22, (int) (length * 2) + 2));
        switch (element) {
            case LIGHTNING -> {
                brush.sprite(b, width * 2.3, 0, art.color(), alpha * .65, 0);
                bolt(brush, a, b, r, u, width * .6, art.color(), art.core(), alpha, age);
                for (int i = 1; i < 4; i++) {
                    V at = a.add(axis.mul(i / 4.0));
                    V end =
                            at.add(r.mul(Math.sin(i * 13 + Math.floor(age)) * width * 3))
                                    .add(u.mul(width * 1.4));
                    bolt(
                            brush,
                            at,
                            end,
                            r,
                            u,
                            width * .22,
                            art.color(),
                            art.core(),
                            alpha * .55,
                            age + i);
                }
            }
            case FIRE -> {
                brush.line(a, b, width * 1.2, 0xff641f, alpha * .28);
                for (int i = 0; i < count; i++) {
                    double t = (i + .3 + (age * .18) % 1) / count;
                    V at =
                            a.add(axis.mul(Math.min(1, t)))
                                    .add(u.mul(Math.sin(i * 2 + age * .3) * width * .35));
                    brush.sprite(
                            at,
                            width * (1.3 + t),
                            SpellAtlas.frame(element, age + i * .5f),
                            color,
                            alpha * .8,
                            Math.sin(i) * .25);
                }
            }
            case WATER -> {
                for (int i = 0; i < count; i++) {
                    double t = i / (double) count;
                    V at = a.add(axis.mul(t)), next = a.add(axis.mul((i + 1.0) / count));
                    brush.line(
                            at,
                            next,
                            width * (.5 + .25 * Math.sin(i * 1.7 - age * .8)),
                            art.color(),
                            alpha * .7);
                    if (i % 3 == 0)
                        brush.face(
                                at,
                                r.mul(width * 1.8),
                                u.mul(width * 1.8),
                                13,
                                art.core(),
                                alpha * .65);
                }
                brush.sprite(b, width * 2.1, tile, color, alpha, age * .08);
            }
            case EARTH -> {
                for (int i = 0; i < count; i++) {
                    double t = (i + .2 + (age * .12) % 1) / count;
                    V at = a.add(axis.mul(Math.min(1, t))).add(r.mul(Math.sin(i * 5) * width * .5));
                    brush.sprite(
                            at,
                            width * (.8 + i % 3 * .25),
                            60 + i % 4,
                            art.color(),
                            alpha,
                            i * 1.7);
                    if (i % 2 == 0)
                        brush.line(
                                at.sub(n.mul(width * 2.8)),
                                at,
                                width * .18,
                                art.core(),
                                alpha * .55);
                }
            }
            case WIND -> {
                for (int lane = 0; lane < 2; lane++) {
                    V last = a;
                    for (int i = 1; i <= count; i++) {
                        double t = i / (double) count,
                                angle = t * length * 1.8 - age * .8 + lane * Math.PI;
                        V at =
                                a.add(axis.mul(t))
                                        .add(r.mul(Math.cos(angle) * width * 2))
                                        .add(u.mul(Math.sin(angle) * width * 2));
                        brush.line(last, at, width * .24, art.core(), alpha * .75);
                        last = at;
                    }
                }
                brush.face(
                        b, r.mul(width * 2.7), u.mul(width * 2.7), tile, art.color(), alpha * .8);
            }
            case LIGHT -> {
                brush.line(a, b, width * .7, art.color(), alpha * .35);
                brush.line(a, b, width * .16, art.core(), alpha);
                for (int i = 0; i < 5; i++) {
                    V at = a.add(axis.mul((i + .5) / 5));
                    brush.face(
                            at,
                            r.mul(width * 1.7),
                            u.mul(width * 1.7),
                            3,
                            art.color(),
                            alpha * .65);
                    brush.sprite(at, width * .9, tile, art.core(), alpha * .8, 0);
                }
            }
            case SHADOW -> {
                for (int lane = 0; lane < 3; lane++) {
                    V last = a;
                    for (int i = 1; i <= count; i++) {
                        double t = i / (double) count, angle = t * 8 - age * .25 + lane * 2.1;
                        V at =
                                a.add(axis.mul(t))
                                        .add(r.mul(Math.sin(angle) * width * 1.5))
                                        .add(u.mul(Math.cos(angle) * width * 1.5));
                        brush.line(
                                last,
                                at,
                                width * .4,
                                lane == 0 ? 0x21102e : art.color(),
                                alpha * .8);
                        last = at;
                    }
                }
                brush.sprite(b, width * 3, tile, color, alpha, age * .12);
            }
            case ARCANE -> {
                brush.line(a, b, width * .3, art.core(), alpha * .75);
                for (int i = 0; i < 4; i++) {
                    double angle = age * .1 + i;
                    V x = r.mul(Math.cos(angle)).add(u.mul(Math.sin(angle))),
                            y = u.mul(Math.cos(angle)).sub(r.mul(Math.sin(angle)));
                    brush.face(
                            a.add(axis.mul((i + .4) / 4)),
                            x.mul(width * (2 + i * .2)),
                            y.mul(width * (2 + i * .2)),
                            7,
                            art.color(),
                            alpha * .9);
                }
                brush.sprite(b, width * 1.4, tile, color, alpha, 0);
            }
            case PURE -> brush.line(a, b, width * .4, art.core(), alpha);
        }
        if (e.kind() == SpellVisual.Kind.TRAIL) {
            brush.sprite(
                    b,
                    width * (element == Element.EARTH ? 2 : 2.8),
                    tile,
                    color,
                    alpha,
                    element == Element.EARTH ? age * .1 : 0);
        }
    }

    static void volume(
            SpellVisual e,
            ShapeVolume shape,
            V origin,
            V normal,
            float p,
            float age,
            double alpha,
            Brush brush) {
        Element element = e.element();
        var art = VisualStyles.of(element);
        V r = axisRight(normal), u = r.cross(normal).unit();
        double radius = e.size();
        java.util.function.Function<V, V> world =
                v -> origin.add(r.mul(v.x())).add(u.mul(v.y())).add(normal.mul(v.z()));
        // Each shape provides different paths; the element chooses strokes and material motion.
        if (e.kind() == SpellVisual.Kind.FAN) {
            int lanes = element == Element.EARTH ? 7 : 9;
            for (int lane = 0; lane < lanes; lane++) {
                double angle =
                        Math.toRadians(shape.aperture()) * (lane / (double) (lanes - 1) - .5);
                V end = new V(Math.sin(angle) * shape.reach(), 0, Math.cos(angle) * shape.reach());
                if (element == Element.LIGHTNING) {
                    bolt(
                            brush,
                            origin.add(normal.mul(.2)),
                            world.apply(end),
                            r,
                            u,
                            .055 * radius,
                            art.color(),
                            art.core(),
                            alpha,
                            age + lane);
                    continue;
                }
                for (int i = 0; i < 6; i++) {
                    double t = .16 + .84 * ((i + (age * .12 + lane * .17) % 1) / 6);
                    V at = end.mul(t);
                    double size = radius * (.10 + .18 * t);
                    if (element == Element.FIRE)
                        at = at.add(new V(0, Math.sin(lane * 3 + i + age * .5) * size * .35, 0));
                    if (element == Element.FIRE) {
                        V along = world.apply(end).sub(origin).unit(),
                                across = along.cross(u).unit(),
                                position = world.apply(at);
                        brush.sprite(position, size * 1.65, 0, 0xff5b09, alpha * .65, 0);
                        brush.face(
                                position,
                                across.mul(size * .95),
                                along.mul(size * 1.9),
                                SpellAtlas.frame(element, age + i + lane),
                                0xffffff,
                                alpha);
                        brush.face(
                                position,
                                u.mul(size * .95),
                                along.mul(size * 1.9),
                                SpellAtlas.frame(element, age + i + lane),
                                0xffffff,
                                alpha * .9);
                        brush.line(
                                position.sub(along.mul(size)),
                                position.add(along.mul(size)),
                                size * .45,
                                0xffb137,
                                alpha * .8);
                        continue;
                    }
                    mark(
                            element,
                            world.apply(at),
                            r,
                            u,
                            size,
                            age + lane + i,
                            alpha * (.5 + .5 * t),
                            art,
                            brush);
                    if (element == Element.WATER
                            || element == Element.WIND
                            || element == Element.LIGHT)
                        brush.line(
                                world.apply(at.sub(end.unit().mul(radius * .3))),
                                world.apply(at),
                                size * (element == Element.WATER ? .4 : .2),
                                art.core(),
                                alpha * .85);
                }
            }
            return;
        }
        if (e.kind() == SpellVisual.Kind.WALL) {
            for (int x = -3; x <= 3; x++)
                for (int y = -2; y <= 2; y++) {
                    double xx = x * radius / 3.5, yy = y * radius / 2.6;
                    if (element == Element.FIRE)
                        yy += ((age * .09 + x * .17) % 1 - .5) * radius * .5;
                    if (element == Element.WATER)
                        yy -= ((age * .12 + x * .13) % 1 - .5) * radius * .5;
                    yy = Math.max(-radius, Math.min(radius, yy));
                    V at = world.apply(new V(xx, yy, 0));
                    mark(element, at, r, u, radius * .22, age + x * 2 + y, alpha * .8, art, brush);
                    if (element == Element.LIGHTNING)
                        bolt(
                                brush,
                                at,
                                world.apply(new V(xx, Math.min(radius, yy + radius * .7), 0)),
                                r,
                                u,
                                .03 * radius,
                                art.color(),
                                art.core(),
                                alpha * .65,
                                age + x);
                }
            return;
        }
        if (e.kind() == SpellVisual.Kind.COLUMN) {
            if (element == Element.WIND) {
                double width = radius * shape.rules().columnRadius() / Fixed.ONE;
                for (int lane = 0; lane < 3; lane++) {
                    V last = null;
                    for (int i = 0; i <= 40; i++) {
                        double t = i / 40.0,
                                angle = t * Math.PI * 5 - age * .35 + lane * Math.PI * 2 / 3,
                                spread = width * (.28 + .7 * t);
                        V at =
                                world.apply(
                                        new V(
                                                Math.cos(angle) * spread,
                                                -radius + 2 * radius * t,
                                                Math.sin(angle) * spread));
                        if (last != null) {
                            brush.line(last, at, radius * .10, art.color(), alpha * .45);
                            brush.line(last, at, radius * .035, art.core(), alpha);
                        }
                        if (i % 5 == 0)
                            brush.sprite(
                                    at,
                                    radius * .22,
                                    SpellAtlas.frame(element, age + i),
                                    art.core(),
                                    alpha * .8,
                                    angle);
                        last = at;
                    }
                }
                return;
            }
            for (int i = 0; i < 28; i++) {
                double t = i / 27.0,
                        angle = t * Math.PI * 5 + age * .24,
                        width = radius * shape.rules().columnRadius() / Fixed.ONE;
                double spread = element == Element.WIND ? width * (.35 + .65 * t) : width * .75;
                V at =
                        world.apply(
                                new V(
                                        Math.cos(angle) * spread,
                                        -radius + 2 * radius * t,
                                        Math.sin(angle) * spread));
                mark(
                        element,
                        at,
                        r,
                        u,
                        radius * (element == Element.FIRE ? .23 : .15),
                        age + i,
                        alpha,
                        art,
                        brush);
                if (element == Element.WIND && i > 0) {
                    double a2 = angle - .65;
                    brush.line(
                            at,
                            world.apply(
                                    new V(
                                            Math.cos(a2) * spread,
                                            -radius + 2 * radius * Math.max(0, t - .04),
                                            Math.sin(a2) * spread)),
                            radius * .06,
                            art.core(),
                            alpha);
                }
            }
            return;
        }
        if (e.kind() == SpellVisual.Kind.CROSS) {
            for (int lane = 0; lane < 4; lane++)
                for (int i = 0; i < 8; i++) {
                    double t = (i + .35 + (age * .1) % 1) / 8, angle = lane * Math.PI / 2;
                    V at =
                            world.apply(
                                    new V(
                                            Math.cos(angle) * radius * t,
                                            0,
                                            Math.sin(angle) * radius * t));
                    mark(element, at, r, u, radius * .18, age + i, alpha, art, brush);
                }
            return;
        }
        // Ring and dome retain their distinct footprint without developer-style wireframe outlines.
        int rings = e.kind() == SpellVisual.Kind.DOME ? 3 : 1;
        for (int row = 0; row < rings; row++)
            for (int i = 0; i < 24; i++) {
                double angle = i * Math.PI / 12 + (element == Element.SHADOW ? -1 : 1) * age * .045;
                double elevation =
                        e.kind() == SpellVisual.Kind.DOME ? (row + .2) / 3 * Math.PI / 2 : 0;
                double rad =
                        radius
                                * Math.cos(elevation)
                                * (element == Element.SHADOW ? 1 - p * .25 : 1);
                V at =
                        world.apply(
                                new V(
                                        Math.cos(angle) * rad,
                                        Math.sin(elevation) * radius,
                                        Math.sin(angle) * rad));
                mark(element, at, r, u, radius * .14, age + i * .4f, alpha, art, brush);
            }
        if (element == Element.ARCANE || element == Element.LIGHT)
            brush.face(origin, r.mul(radius), normal.mul(radius), 7, art.color(), alpha * .7);
    }

    private static void mark(
            Element e,
            V at,
            V r,
            V u,
            double size,
            float age,
            double alpha,
            VisualStyles.ElementStyle art,
            Brush brush) {
        int tile = SpellAtlas.frame(e, age), color = color(e, art);
        switch (e) {
            case EARTH -> {
                brush.sprite(at, size * (1 + .2 * Math.sin(age)), tile, color, alpha, age * .08);
            }
            case WIND -> {
                brush.face(at, r.mul(size * 1.8), u.mul(size * .8), tile, art.core(), alpha * .95);
            }
            case LIGHT -> {
                brush.sprite(at, size, tile, art.core(), alpha, age * .06);
            }
            case ARCANE -> {
                brush.face(at, r.mul(size), u.mul(size), 7, art.color(), alpha);
                brush.sprite(at, size * .35, tile, color, alpha, age * .1);
            }
            case LIGHTNING -> {
                V end = at.add(r.mul(size * 1.4)).add(u.mul(size * .7));
                bolt(brush, at, end, r, u, size * .15, art.color(), art.core(), alpha, age);
            }
            case SHADOW -> {
                brush.sprite(at, size * 1.1, tile, color, alpha * .9, -age * .08);
            }
            case FIRE -> {
                brush.sprite(at, size * 1.25, tile, color, alpha, 0);
                brush.sprite(
                        at.add(u.mul(size * .8)),
                        size * .7,
                        SpellAtlas.frame(e, age + 2),
                        0xffe8ab,
                        alpha * .55,
                        0);
            }
            default -> brush.sprite(at, size, tile, color, alpha, age * .04);
        }
    }

    static void shield(
            SpellVisual e, V origin, V normal, float p, float age, double alpha, Brush brush) {
        var art = VisualStyles.of(e.element());
        V r = axisRight(normal), u = r.cross(normal).unit();
        double radius = e.size();
        brush.face(origin, r.mul(radius * 1.03), u.mul(radius * 1.03), 3, art.color(), alpha * .7);
        brush.face(
                origin,
                r.mul(radius),
                u.mul(radius),
                7,
                art.color(),
                alpha * (e.element() == Element.ARCANE ? .9 : .25));
        for (int i = 0; i < 18; i++) {
            double a = i * Math.PI / 9 + age * .035;
            V at =
                    origin.add(r.mul(Math.cos(a) * radius * .92))
                            .add(u.mul(Math.sin(a) * radius * .92));
            mark(e.element(), at, r, u, radius * .13, age + i * .5f, alpha, art, brush);
        }
        if (e.element() == Element.WATER || e.element() == Element.SHADOW)
            brush.face(
                    origin,
                    r.mul(radius * .85),
                    u.mul(radius * .85),
                    SpellAtlas.frame(e.element(), age),
                    color(e.element(), art),
                    alpha * .5);
        if (e.element() == Element.EARTH) {
            for (int i = 0; i < 7; i++) {
                double a = i * Math.PI / 3;
                V at =
                        origin.add(r.mul(i == 6 ? 0 : Math.cos(a) * radius * .47))
                                .add(u.mul(i == 6 ? 0 : Math.sin(a) * radius * .47));
                brush.face(
                        at,
                        r.mul(radius * .3),
                        u.mul(radius * .3),
                        60 + i % 4,
                        art.color(),
                        alpha * .85);
            }
        }
        if (e.element() == Element.WIND)
            for (int i = 0; i < 3; i++)
                arc(
                        brush,
                        origin,
                        r,
                        u,
                        radius * (.4 + i * .25),
                        age * .2 + i,
                        age * .2 + i + Math.PI * 1.4,
                        radius * .015,
                        art.core(),
                        alpha * .8,
                        12);
        if (e.element() == Element.LIGHTNING)
            for (int i = 0; i < 5; i++) {
                double x = (i - 2) * radius * .3;
                bolt(
                        brush,
                        origin.add(r.mul(x)).sub(u.mul(radius * .7)),
                        origin.add(r.mul(x)).add(u.mul(radius * .7)),
                        r,
                        u,
                        radius * .025,
                        art.color(),
                        art.core(),
                        alpha * .8,
                        age + i);
            }
    }
}
