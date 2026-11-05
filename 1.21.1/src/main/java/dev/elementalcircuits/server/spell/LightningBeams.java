package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.adapter.resources.ElementalComboResources;
import dev.elementalcircuits.application.observation.SpellSound;
import dev.elementalcircuits.application.observation.SpellVisual;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.combat.ElementalCombos;
import dev.elementalcircuits.domain.mana.Packet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

import java.util.*;

/** Instant authoritative chain, bounded by paid energy, total range, LOS and visited identities. */
final class LightningBeams {
    private LightningBeams() {}

    static void cast(
            ServerLevel level,
            UUID owner,
            SpellEffects.Trace first,
            Vec3 direction,
            double remaining,
            int radius,
            Packet packet,
            long strength) {
        var rules = ElementalComboResources.rules();
        int jumps = ElementalCombos.jumps(packet, rules);
        Set<UUID> visited = new HashSet<>();
        var hit = first;
        var current = packet;
        for (int hop = 0; hop <= jumps && current != null; hop++) {
            if (hit.entity() != null) visited.add(hit.entity().getUUID());
            Vec3 from = hit.position();
            LivingEntity next = null;
            double reach = Math.min(rules.lightningReach(), remaining);
            if (hop < jumps && hit.entity() instanceof LivingEntity && reach > .05) {
                var targets =
                        level.getEntitiesOfClass(
                                LivingEntity.class,
                                new AABB(from, from).inflate(reach),
                                e ->
                                        SpellEffects.eligible(level, owner, e)
                                                && !visited.contains(e.getUUID())
                                                && e.getBoundingBox()
                                                                .getCenter()
                                                                .distanceToSqr(from)
                                                        <= reach * reach
                                                && SpellEffects.loadedBetween(
                                                        level, from, e.getBoundingBox().getCenter())
                                                && level.clip(
                                                                        new ClipContext(
                                                                                from,
                                                                                e.getBoundingBox()
                                                                                        .getCenter(),
                                                                                ClipContext.Block
                                                                                        .COLLIDER,
                                                                                ClipContext.Fluid
                                                                                        .NONE,
                                                                                net.minecraft.world
                                                                                        .phys.shapes
                                                                                        .CollisionContext
                                                                                        .empty()))
                                                                .getType()
                                                        == HitResult.Type.MISS);
                targets.sort(
                        Comparator.comparingDouble(
                                        (LivingEntity e) ->
                                                e.getBoundingBox().getCenter().distanceToSqr(from))
                                .thenComparing(Entity::getUUID));
                if (!targets.isEmpty()) next = targets.get(0);
            }
            var share = ElementalCombos.share(current.mana(), next != null, rules);
            if (!share.contact().empty())
                WorldSpells.resolveBeamContact(
                        level,
                        owner,
                        hit,
                        direction,
                        radius,
                        new Packet(share.contact(), current.spell()),
                        strength);
            if (next == null || share.onward().empty()) break;
            Vec3 to = next.getBoundingBox().getCenter();
            double length = from.distanceTo(to);
            remaining -= length;
            SpellVisuals.observe(
                    level,
                    SpellVisual.Kind.BEAM,
                    Element.LIGHTNING,
                    from,
                    to,
                    1,
                    Math.max(1, jumps - hop));
            SpellSounds.observe(level, SpellSound.Kind.IMPACT, Element.LIGHTNING, to);
            direction = to.subtract(from).normalize();
            hit = new SpellEffects.Trace(to, next, null, true);
            current = new Packet(share.onward(), current.spell());
        }
    }
}
