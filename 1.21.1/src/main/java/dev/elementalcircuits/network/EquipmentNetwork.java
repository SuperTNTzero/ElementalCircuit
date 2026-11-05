package dev.elementalcircuits.network;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.client.input.EquipmentClient;
import dev.elementalcircuits.server.equipment.EquipmentCasting;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

/** Small intentions only; item ownership, slots and triggers are never trusted from the client. */
public final class EquipmentNetwork {
    public record Request(UUID identity, int slot, int action) implements CustomPacketPayload {
        public static final Type<Request> TYPE =
                new Type<>(
                        ResourceLocation.fromNamespaceAndPath(
                                ElementalCircuits.ID, "equipment_request"));

        public Type<Request> type() {
            return TYPE;
        }
    }

    public record State(String json) implements CustomPacketPayload {
        public static final Type<State> TYPE =
                new Type<>(
                        ResourceLocation.fromNamespaceAndPath(
                                ElementalCircuits.ID, "equipment_state"));

        public Type<State> type() {
            return TYPE;
        }
    }

    public static void register(PayloadRegistrar r) {
        r.playToServer(
                Request.TYPE,
                StreamCodec.of(
                        (b, p) -> {
                            b.writeUUID(p.identity());
                            b.writeVarInt(p.slot());
                            b.writeVarInt(p.action());
                        },
                        b -> new Request(b.readUUID(), b.readVarInt(), b.readVarInt())),
                (p, c) ->
                        EquipmentCasting.request(
                                (ServerPlayer) c.player(), p.identity(), p.slot(), p.action()));
        r.playToClient(
                State.TYPE,
                StreamCodec.of(
                        (b, p) -> b.writeUtf(p.json(), 65536), b -> new State(b.readUtf(65536))),
                (p, c) -> EquipmentClient.state(p.json()));
    }

    public static void send(UUID id, int slot, int action) {
        PacketDistributor.sendToServer(new Request(id, slot, action));
    }

    public static void sync(ServerPlayer p) {
        PacketDistributor.sendToPlayer(
                p, new State(new com.google.gson.Gson().toJson(EquipmentCasting.inspect(p))));
    }

    private EquipmentNetwork() {}
}
