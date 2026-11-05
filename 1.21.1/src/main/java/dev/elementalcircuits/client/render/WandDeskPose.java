package dev.elementalcircuits.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.elementalcircuits.block.WorkstationGeometry;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Fits the actual baked wand onto the desktop once per model, including after resource reload. */
public record WandDeskPose(
        double centerX,
        double centerY,
        double centerZ,
        double scale,
        double lift,
        double turnDegrees) {
    private static final Map<BakedModel, WandDeskPose> CACHE = new WeakHashMap<>();

    public static double angle() {
        return WorkstationGeometry.desk().tiltDegrees();
    }

    public static double surface() {
        return WorkstationGeometry.desk().surfaceY()
                + (.5 - WorkstationGeometry.desk().previewZ()) * Math.tan(Math.toRadians(angle()));
    }

    public static WandDeskPose of(BakedModel model) {
        return CACHE.computeIfAbsent(model, WandDeskPose::fit);
    }

    public static List<Vec3> vertices(BakedModel model) {
        var points = new ArrayList<Vec3>();
        var sides = new ArrayList<Direction>(Arrays.asList(Direction.values()));
        sides.add(null);
        for (var side : sides)
            for (var quad : model.getQuads(null, side, RandomSource.create(0))) {
                int[] data = quad.getVertices();
                int stride = data.length / 4;
                for (int i = 0; i < 4; i++)
                    points.add(
                            new Vec3(
                                    Float.intBitsToFloat(data[i * stride]),
                                    Float.intBitsToFloat(data[i * stride + 1]),
                                    Float.intBitsToFloat(data[i * stride + 2])));
            }
        return points;
    }

    private static WandDeskPose fit(BakedModel model) {
        var points = vertices(model);
        if (points.isEmpty()) return new WandDeskPose(.5, .5, .5, 0, 0, 90);
        double x0 = Double.POSITIVE_INFINITY, y0 = x0, z0 = x0, x1 = -x0, y1 = -x0, z1 = -x0;
        for (var p : points) {
            x0 = Math.min(x0, p.x);
            x1 = Math.max(x1, p.x);
            y0 = Math.min(y0, p.y);
            y1 = Math.max(y1, p.y);
            z0 = Math.min(z0, p.z);
            z1 = Math.max(z1, p.z);
        }
        double cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, cz = (z0 + z1) / 2;
        double cos = Math.cos(Math.toRadians(angle())), sin = Math.sin(Math.toRadians(angle()));
        // The lower convex hull supplies two contacts spanning the centre. A bulky
        // head and a thin tail then rest together instead of lifting the entire shaft.
        var sorted =
                points.stream()
                        .sorted(
                                Comparator.comparingDouble((Vec3 p) -> p.y)
                                        .thenComparingDouble(p -> p.x))
                        .toList();
        var hull = new ArrayList<Vec3>();
        double lastY = Double.NaN;
        for (var p : sorted) {
            if (p.y == lastY) continue;
            lastY = p.y;
            while (hull.size() >= 2) {
                var a = hull.get(hull.size() - 2);
                var b = hull.getLast();
                if ((b.y - a.y) * (p.x - a.x) - (b.x - a.x) * (p.y - a.y) > 0) break;
                hull.removeLast();
            }
            hull.add(p);
        }
        double roll = 0;
        for (int i = 1; i < hull.size(); i++) {
            var a = hull.get(i - 1);
            var b = hull.get(i);
            if (a.y <= cy && cy <= b.y) {
                roll =
                        Math.clamp(
                                Math.atan2(b.x - a.x, b.y - a.y),
                                Math.toRadians(-15),
                                Math.toRadians(15));
                break;
            }
        }
        double rc = Math.cos(roll),
                rs = Math.sin(roll),
                minimum = Double.POSITIVE_INFINITY,
                top = Double.NEGATIVE_INFINITY;
        double left = Double.POSITIVE_INFINITY, right = -left;
        for (var p : points) {
            double normal = rc * (p.x - cx) - rs * (p.y - cy),
                    along = -rs * (p.x - cx) - rc * (p.y - cy);
            minimum = Math.min(minimum, normal);
            top = Math.max(top, cos * normal - sin * (p.z - cz));
            left = Math.min(left, along);
            right = Math.max(right, along);
        }
        double support = -minimum;
        double gap = WorkstationGeometry.desk().clearance(),
                scale = WorkstationGeometry.desk().preferredScale();
        scale =
                Math.min(
                        scale,
                        (WorkstationGeometry.desk().ceiling() - surface() - cos * gap)
                                / (cos * support + top));
        scale = Math.min(scale, 1.6 / Math.max(.001, right - left));
        scale = Math.min(scale, .65 / Math.max(.001, z1 - z0));
        return new WandDeskPose(
                cx, cy, cz, Math.max(0, scale), support * scale + gap, 90 + Math.toDegrees(roll));
    }

    public void apply(PoseStack pose) {
        pose.translate(
                WorkstationGeometry.desk().previewX(),
                surface(),
                WorkstationGeometry.desk().previewZ());
        pose.mulPose(Axis.XP.rotationDegrees((float) angle()));
        pose.translate(0, lift, 0);
        pose.mulPose(Axis.ZP.rotationDegrees((float) turnDegrees));
        pose.scale((float) scale, (float) scale, (float) scale);
        // ItemRenderer's NONE context supplies its own -0.5 origin translation.
        pose.translate(.5 - centerX, .5 - centerY, .5 - centerZ);
    }
}
