package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.adapter.resources.ShapeResources;
import dev.elementalcircuits.application.observation.SpellVisual;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.ShapeVolume;
import dev.elementalcircuits.domain.casting.SpellPayloads;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.domain.mana.Packet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** One prepaid elemental pulse. Repetition belongs to the circuit, never a cosmetic timer. */
public final class ShapeSpells {
    private ShapeSpells() {}

    public static void cast(
            ServerLevel level,
            UUID owner,
            Vec3 origin,
            Vec3 direction,
            int range,
            int radius,
            Mixture mana,
            SpellSpec spell,
            long strength) {
        var rules = ShapeResources.rules();
        for (var packet : SpellPayloads.split(new Packet(mana, spell))) {
            var shape = spell.shape();
            Vec3 center = origin;
            if (shape != SpellSpec.Shape.FAN && spell.method() == SpellSpec.Method.DIRECTED) {
                var hit =
                        SpellEffects.trace(
                                level,
                                owner,
                                origin,
                                origin.add(direction.scale(range)),
                                true,
                                false,
                                SpellPayloads.phased(packet.mana()));
                center =
                        hit.entity() == null
                                ? hit.position()
                                        .subtract(direction.scale(hit.block() != null ? .04 : 0))
                                : hit.entity().getBoundingBox().getCenter();
            } else if (shape == SpellSpec.Shape.RING
                    || shape == SpellSpec.Shape.CROSS
                    || shape == SpellSpec.Shape.DOME)
                center = center.add(0, -rules.groundOffset() / (double) Fixed.ONE, 0);
            var frame =
                    ShapeFrame.of(
                            center,
                            direction,
                            new ShapeVolume(
                                    shape,
                                    radius,
                                    rules,
                                    spell.spatial().aperture(rules.fanDegrees())));
            ElementInteractions.volume(
                    level, owner, frame, packet.mana(), strength, ContactMagic.of(packet.spell()));
            SpellVisuals.observe(
                    level,
                    SpellVisual.Kind.valueOf(shape.name()),
                    packet.mana().dominant(),
                    center,
                    frame.normal(),
                    radius,
                    1,
                    (float) spell.spatial().aperture(rules.fanDegrees()),
                    spell.customEffectId());
        }
    }
}
