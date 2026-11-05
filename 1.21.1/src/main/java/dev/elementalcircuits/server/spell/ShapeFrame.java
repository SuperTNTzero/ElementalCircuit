package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.ShapeVolume;
import dev.elementalcircuits.domain.casting.SpellSpec;

import net.minecraft.world.phys.*;

/** Minecraft coordinate adapter for the shared local-space volume predicate. */
public record ShapeFrame(Vec3 center, Vec3 normal, Vec3 right, Vec3 up, ShapeVolume volume) {
    public static ShapeFrame of(Vec3 center, Vec3 direction, ShapeVolume volume) {
        Vec3 n =
                volume.shape() == SpellSpec.Shape.FAN
                        ? direction
                        : new Vec3(direction.x, 0, direction.z);
        if (n.lengthSqr() < 1e-10) n = new Vec3(0, 0, 1);
        n = n.normalize();
        Vec3 right =
                n.cross(Math.abs(n.y) > .99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        return new ShapeFrame(center, n, right, right.cross(n).normalize(), volume);
    }

    public boolean contains(Vec3 point) {
        var d = point.subtract(center);
        return volume.contains(d.dot(right), d.dot(up), d.dot(normal));
    }

    public Vec3 point(double x, double y, double z) {
        return center.add(right.scale(x)).add(up.scale(y)).add(normal.scale(z));
    }

    public AABB bounds() {
        var b = volume.bounds();
        double minX = Double.POSITIVE_INFINITY,
                minY = minX,
                minZ = minX,
                maxX = Double.NEGATIVE_INFINITY,
                maxY = maxX,
                maxZ = maxX;
        for (double x : new double[] {b.minX(), b.maxX()})
            for (double y : new double[] {b.minY(), b.maxY()})
                for (double z : new double[] {b.minZ(), b.maxZ()}) {
                    var p = point(x, y, z);
                    minX = Math.min(minX, p.x);
                    minY = Math.min(minY, p.y);
                    minZ = Math.min(minZ, p.z);
                    maxX = Math.max(maxX, p.x);
                    maxY = Math.max(maxY, p.y);
                    maxZ = Math.max(maxZ, p.z);
                }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(.001);
    }
}
