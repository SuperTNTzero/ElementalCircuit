package dev.elementalcircuits.network;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.client.render.FixedRelationRenderer;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

/** Bounded, disposable view of loaded anchors. No simulation state is writable by the client. */
public final class RelationNetwork {
    public record Node(BlockPos pos, int slot, boolean powered, long charge) {}

    public record Edge(
            int from, int to, int fromSlot, int toSlot, boolean signal, boolean active) {}

    public record View(ResourceLocation dimension, List<Node> nodes, List<Edge> edges)
            implements CustomPacketPayload {
        public static final Type<View> TYPE =
                new Type<>(
                        ResourceLocation.fromNamespaceAndPath(ElementalCircuits.ID, "relations"));

        public View {
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
            if (nodes.size() > 64 || edges.size() > 128)
                throw new IllegalArgumentException("Relation view too large");
            for (var edge : edges)
                if (edge.from < 0
                        || edge.to < 0
                        || edge.from >= nodes.size()
                        || edge.to >= nodes.size())
                    throw new IllegalArgumentException("Invalid relation endpoint");
        }

        @Override
        public Type<View> type() {
            return TYPE;
        }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                View.TYPE,
                StreamCodec.of(
                        (b, p) -> {
                            b.writeResourceLocation(p.dimension);
                            b.writeCollection(
                                    p.nodes,
                                    (out, n) -> {
                                        out.writeBlockPos(n.pos);
                                        out.writeVarInt(n.slot);
                                        out.writeBoolean(n.powered);
                                        out.writeLong(n.charge);
                                    });
                            b.writeCollection(
                                    p.edges,
                                    (out, e) -> {
                                        out.writeVarInt(e.from);
                                        out.writeVarInt(e.to);
                                        out.writeVarInt(e.fromSlot);
                                        out.writeVarInt(e.toSlot);
                                        out.writeBoolean(e.signal);
                                        out.writeBoolean(e.active);
                                    });
                        },
                        b ->
                                new View(
                                        b.readResourceLocation(),
                                        b.readCollection(
                                                net.minecraft.network.FriendlyByteBuf.limitValue(
                                                        java.util.ArrayList::new, 64),
                                                in ->
                                                        new Node(
                                                                in.readBlockPos(),
                                                                in.readVarInt(),
                                                                in.readBoolean(),
                                                                in.readLong())),
                                        b.readCollection(
                                                net.minecraft.network.FriendlyByteBuf.limitValue(
                                                        java.util.ArrayList::new, 128),
                                                in ->
                                                        new Edge(
                                                                in.readVarInt(),
                                                                in.readVarInt(),
                                                                in.readVarInt(),
                                                                in.readVarInt(),
                                                                in.readBoolean(),
                                                                in.readBoolean())))),
                (p, c) -> FixedRelationRenderer.accept(p));
    }

    private RelationNetwork() {}
}
