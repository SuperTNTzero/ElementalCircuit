package dev.elementalcircuits.client.effect;

import dev.elementalcircuits.client.effect.SpellGeometry.V;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.ShapeVolume;

/** Bounded local-space silhouettes. The volume profile is shared with authoritative membership. */
public final class VolumeGeometry {
    private static final int SEGMENTS = 32;
    private static final double[] SIN = new double[SEGMENTS + 1], COS = new double[SEGMENTS + 1];

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            SIN[i] = Math.sin(i * Math.PI * 2 / SEGMENTS);
            COS[i] = Math.cos(i * Math.PI * 2 / SEGMENTS);
        }
    }

    @FunctionalInterface
    public interface Line {
        void draw(V a, V b);
    }

    @FunctionalInterface
    public interface Face {
        void draw(V a, V b, V c, V d);
    }

    private VolumeGeometry() {}

    public static void emit(ShapeVolume volume, Line line, Face face) {
        double r = volume.radius();
        var rules = volume.rules();
        var b = volume.bounds();
        switch (volume.shape()) {
            case FAN -> {
                double half = Math.toRadians(volume.aperture() / 2.0), reach = volume.reach();
                for (int y = -1; y <= 1; y += 2) {
                    V last = new V(-Math.sin(half) * reach, y * b.maxY(), Math.cos(half) * reach);
                    line.draw(new V(0, y * b.maxY(), 0), last);
                    for (int i = 1; i <= 16; i++) {
                        double t = -half + i * 2 * half / 16;
                        V next = new V(Math.sin(t) * reach, y * b.maxY(), Math.cos(t) * reach);
                        line.draw(last, next);
                        last = next;
                    }
                    line.draw(last, new V(0, y * b.maxY(), 0));
                }
                for (int i = 0; i <= 6; i++) {
                    double t = -half + i * 2 * half / 6;
                    V end = new V(Math.sin(t) * reach, 0, Math.cos(t) * reach);
                    line.draw(new V(0, 0, 0), end);
                }
                for (int i = 0; i < 12; i++) {
                    double t = -half + i * 2 * half / 12, n = t + 2 * half / 12;
                    face.draw(
                            new V(0, 0, 0),
                            new V(Math.sin(t) * reach, 0, Math.cos(t) * reach),
                            new V(Math.sin(n) * reach, 0, Math.cos(n) * reach),
                            new V(0, 0, 0));
                }
            }
            case WALL -> {
                for (int z = -1; z <= 1; z += 2) {
                    double depth = z * b.maxZ();
                    rect(
                            line,
                            new V(-r, -r, depth),
                            new V(r, -r, depth),
                            new V(r, r, depth),
                            new V(-r, r, depth));
                }
                for (int x = -1; x <= 1; x += 2)
                    for (int y = -1; y <= 1; y += 2)
                        line.draw(new V(x * r, y * r, b.minZ()), new V(x * r, y * r, b.maxZ()));
                for (int i = -1; i <= 1; i++) {
                    double t = i * r / 2;
                    line.draw(new V(-r, t, 0), new V(r, t, 0));
                    line.draw(new V(t, -r, 0), new V(t, r, 0));
                }
                face.draw(new V(-r, -r, 0), new V(r, -r, 0), new V(r, r, 0), new V(-r, r, 0));
            }
            case RING -> {
                double inner = r * rules.ringInner() / Fixed.ONE;
                circle(line, r, 0, 1);
                circle(line, inner, 0, 1);
                for (int i = 0; i < SEGMENTS; i++)
                    face.draw(
                            new V(COS[i] * inner, 0, SIN[i] * inner),
                            new V(COS[i] * r, 0, SIN[i] * r),
                            new V(COS[i + 1] * r, 0, SIN[i + 1] * r),
                            new V(COS[i + 1] * inner, 0, SIN[i + 1] * inner));
                for (int i = 0; i < 8; i++) {
                    int j = i * 4;
                    line.draw(
                            new V(COS[j] * inner, 0, SIN[j] * inner),
                            new V(COS[j] * r, 0, SIN[j] * r));
                }
            }
            case COLUMN -> {
                double width = b.maxX();
                circle(line, width, -r, 1);
                circle(line, width, r, 1);
                circle(line, width, 0, 1);
                for (int i = 0; i < 8; i++) {
                    int j = i * 4;
                    line.draw(
                            new V(COS[j] * width, -r, SIN[j] * width),
                            new V(COS[j] * width, r, SIN[j] * width));
                }
                for (int i = 0; i < SEGMENTS; i += 4)
                    face.draw(
                            new V(COS[i] * width, -r, SIN[i] * width),
                            new V(COS[i + 4] * width, -r, SIN[i + 4] * width),
                            new V(COS[i + 4] * width, r, SIN[i + 4] * width),
                            new V(COS[i] * width, r, SIN[i] * width));
            }
            case CROSS -> {
                double w = Math.max(.25, r * rules.crossWidth() / Fixed.ONE);
                double[][] outline = {
                    {-w, -r}, {w, -r}, {w, -w}, {r, -w}, {r, w}, {w, w}, {w, r}, {-w, r}, {-w, w},
                    {-r, w}, {-r, -w}, {-w, -w}
                };
                for (int i = 0; i < outline.length; i++) {
                    var a = outline[i];
                    var c = outline[(i + 1) % outline.length];
                    line.draw(new V(a[0], 0, a[1]), new V(c[0], 0, c[1]));
                }
                face.draw(new V(-w, 0, -r), new V(w, 0, -r), new V(w, 0, r), new V(-w, 0, r));
                face.draw(new V(-r, 0, -w), new V(r, 0, -w), new V(r, 0, w), new V(-r, 0, w));
            }
            case DOME -> {
                circle(line, r, 0, 1);
                circle(line, r * rules.domeInner() / Fixed.ONE, 0, 1);
                for (int j = 1; j < 4; j++) {
                    double t = j * Math.PI / 8;
                    circle(line, r * Math.cos(t), r * Math.sin(t), 1);
                }
                for (int j = 0; j < 8; j++) {
                    double angle = j * Math.PI / 4;
                    V last = new V(Math.cos(angle) * r, 0, Math.sin(angle) * r);
                    for (int i = 1; i <= 8; i++) {
                        double t = i * Math.PI / 16;
                        V next =
                                new V(
                                        Math.cos(angle) * r * Math.cos(t),
                                        r * Math.sin(t),
                                        Math.sin(angle) * r * Math.cos(t));
                        line.draw(last, next);
                        last = next;
                    }
                }
                for (int j = 0; j < 4; j++)
                    for (int i = 0; i < 16; i++) {
                        double t = j * Math.PI / 8,
                                n = (j + 1) * Math.PI / 8,
                                a = i * Math.PI / 8,
                                c = (i + 1) * Math.PI / 8;
                        face.draw(
                                sphere(r, t, a), sphere(r, t, c), sphere(r, n, c), sphere(r, n, a));
                    }
            }
            default ->
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.effect._volume_geometry.001"));
        }
    }

    private static V sphere(double r, double elevation, double angle) {
        return new V(
                Math.cos(angle) * r * Math.cos(elevation),
                r * Math.sin(elevation),
                Math.sin(angle) * r * Math.cos(elevation));
    }

    private static void circle(Line line, double r, double y, double scale) {
        for (int i = 0; i < SEGMENTS; i++)
            line.draw(
                    new V(COS[i] * r, y, SIN[i] * r * scale),
                    new V(COS[i + 1] * r, y, SIN[i + 1] * r * scale));
    }

    private static void rect(Line line, V a, V b, V c, V d) {
        line.draw(a, b);
        line.draw(b, c);
        line.draw(c, d);
        line.draw(d, a);
    }
}
