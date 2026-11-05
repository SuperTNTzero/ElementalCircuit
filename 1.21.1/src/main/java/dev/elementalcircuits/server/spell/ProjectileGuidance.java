package dev.elementalcircuits.server.spell;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

import java.util.*;

/**
 * Server-only target selection and bounded turning. Neither particles nor client aim decide a hit.
 */
final class ProjectileGuidance {
    record Result(Vec3 direction, UUID target) {}

    private ProjectileGuidance() {}

    private static boolean visible(
            ServerLevel level, UUID owner, Vec3 at, LivingEntity e, int range) {
        Vec3 center = e.getBoundingBox().getCenter();
        return SpellEffects.eligible(level, owner, e)
                && center.distanceToSqr(at) <= range * range
                && SpellEffects.loadedBetween(level, at, center)
                && level.clip(
                                        new ClipContext(
                                                at,
                                                center,
                                                ClipContext.Block.COLLIDER,
                                                ClipContext.Fluid.NONE,
                                                net.minecraft.world.phys.shapes.CollisionContext
                                                        .empty()))
                                .getType()
                        == HitResult.Type.MISS;
    }

    static Result update(
            ServerLevel level,
            UUID owner,
            Vec3 at,
            Vec3 direction,
            int range,
            int degrees,
            UUID locked) {
        return update(level, owner, at, direction, range, degrees, locked, Set.of());
    }

    static Result update(
            ServerLevel level,
            UUID owner,
            Vec3 at,
            Vec3 direction,
            int range,
            int degrees,
            UUID locked,
            Set<UUID> excluded) {
        if (range == 0) return new Result(direction, null);
        Entity old = locked == null ? null : level.getEntity(locked);
        LivingEntity target =
                old instanceof LivingEntity living
                                && !excluded.contains(living.getUUID())
                                && visible(level, owner, at, living, range)
                        ? living
                        : null;
        if (target == null) {
            var list =
                    level.getEntitiesOfClass(
                            LivingEntity.class,
                            new AABB(at, at).inflate(range),
                            e ->
                                    !excluded.contains(e.getUUID())
                                            && visible(level, owner, at, e, range)
                                            && e.getBoundingBox()
                                                            .getCenter()
                                                            .subtract(at)
                                                            .dot(direction)
                                                    > 0);
            list.sort(
                    Comparator.comparingDouble(
                                    (LivingEntity e) ->
                                            e.getBoundingBox().getCenter().distanceToSqr(at))
                            .thenComparing(Entity::getUUID));
            if (!list.isEmpty()) target = list.get(0);
        }
        if (target == null) return new Result(direction, null);
        Vec3 desired = target.getBoundingBox().getCenter().subtract(at).normalize();
        if (desired.lengthSqr() < 1e-12) return new Result(direction, target.getUUID());
        double dot = Math.max(-1, Math.min(1, direction.dot(desired))),
                angle = Math.acos(dot),
                turn = Math.min(angle, Math.toRadians(degrees));
        if (angle < 1e-9) return new Result(direction, target.getUUID());
        Vec3 tangent = desired.subtract(direction.scale(dot));
        if (tangent.lengthSqr() < 1e-12)
            tangent =
                    direction.cross(
                            Math.abs(direction.y) > .99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0));
        Vec3 result =
                direction
                        .scale(Math.cos(turn))
                        .add(tangent.normalize().scale(Math.sin(turn)))
                        .normalize();
        return new Result(result, target.getUUID());
    }
}
