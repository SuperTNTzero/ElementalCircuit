package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.adapter.resources.RadianceResources;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.SpellPayloads;
import dev.elementalcircuits.domain.mana.Packet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.*;

final class PathIllumination {
    private PathIllumination() {}

    static Packet beam(
            ServerLevel level, UUID owner, Vec3 from, Vec3 to, Packet packet, long strength) {
        SpellPayloads.Lit split =
                SpellPayloads.illumination(packet, strength, RadianceResources.rules());
        int budget = split.ticks();
        Set<BlockPos> seen = new HashSet<>();
        int steps = Math.min(256, Math.max(1, (int) Math.ceil(from.distanceTo(to) * 2)));
        for (int i = 0; i <= steps && budget > 0; i++) {
            BlockPos pos = BlockPos.containing(from.lerp(to, i / (double) steps));
            if (seen.add(pos)) {
                budget--;
                WorldIllumination.get(level)
                        .glow(level, owner, pos, RadianceResources.rules().trailLife());
            }
        }
        return split.impact();
    }
}
