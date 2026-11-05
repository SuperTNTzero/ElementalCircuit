package dev.elementalcircuits.server.circuit;

import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.network.RelationNetwork;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/** Observes loaded anchors only, without chunk loads or retaining world/player references. */
public final class FixedRelationSight {
    public static void sync(ServerPlayer player) {
        if (!dev.elementalcircuits.adapter.compat.CuriosEquipment.monocleWorn(player)) return;
        var level = player.serverLevel();
        var nodes = new ArrayList<RelationNetwork.Node>();
        var indices = new HashMap<UUID, Integer>();
        for (var block :
                WorldCircuits.blocks(level).stream()
                        .filter(
                                b ->
                                        player.distanceToSqr(
                                                        net.minecraft.world.phys.Vec3.atCenterOf(
                                                                b.getBlockPos()))
                                                <= 64 * 64)
                        .sorted(
                                Comparator.comparingDouble(
                                        b ->
                                                player.distanceToSqr(
                                                        net.minecraft.world.phys.Vec3.atCenterOf(
                                                                b.getBlockPos()))))
                        .toList()) {
            if (nodes.size() == 64) break;
            try {
                var a = block.assembly();
                if (indices.containsKey(a.identity())) continue;
                indices.put(a.identity(), nodes.size());
                nodes.add(
                        new RelationNetwork.Node(
                                block.getBlockPos(),
                                a.selected(),
                                a.circuit(a.selected()).powered(),
                                block.available()));
            } catch (IllegalArgumentException ignored) {
                /* Invalid anchors must not break player ticking. */
            }
        }
        var edges = new ArrayList<RelationNetwork.Edge>();
        try {
            var links = WorldCircuitLinks.get(level);
            for (var link : links.graph().connections()) {
                var from = indices.get(link.from().carrier());
                var to = indices.get(link.to().carrier());
                if (from == null || to == null || edges.size() == 128) continue;
                if (!links.anchored(link.from().carrier(), nodes.get(from).pos())
                        || !links.anchored(link.to().carrier(), nodes.get(to).pos())) continue;
                boolean signal = link.payload() == Route.Payload.SIGNAL;
                edges.add(
                        new RelationNetwork.Edge(
                                from,
                                to,
                                link.from().slot(),
                                link.to().slot(),
                                signal,
                                signal ? link.valid() && link.signal() : link.transit() != null));
            }
        } catch (IllegalArgumentException ignored) {
            /* Corrupt links already disabled by the authority. */
        }
        PacketDistributor.sendToPlayer(
                player, new RelationNetwork.View(level.dimension().location(), nodes, edges));
    }

    private FixedRelationSight() {}
}
