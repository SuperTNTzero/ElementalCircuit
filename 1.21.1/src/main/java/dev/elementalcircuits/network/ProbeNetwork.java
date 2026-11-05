package dev.elementalcircuits.network;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.application.observation.SurveyReport;
import dev.elementalcircuits.client.screen.ProbeScreen;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

public final class ProbeNetwork {
    public record Report(UUID token, ResourceLocation dimension, String json, boolean open)
            implements CustomPacketPayload {
        public static final Type<Report> TYPE =
                new Type<>(
                        ResourceLocation.fromNamespaceAndPath(
                                ElementalCircuits.ID, "probe_report"));

        @Override
        public Type<Report> type() {
            return TYPE;
        }
    }

    private ProbeNetwork() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                Report.TYPE,
                StreamCodec.of(
                        (b, p) -> {
                            b.writeUUID(p.token);
                            b.writeResourceLocation(p.dimension);
                            b.writeUtf(p.json, 16384);
                            b.writeBoolean(p.open);
                        },
                        b ->
                                new Report(
                                        b.readUUID(),
                                        b.readResourceLocation(),
                                        b.readUtf(16384),
                                        b.readBoolean())),
                (p, c) -> ProbeScreen.accept(p));
    }

    public static void send(ServerPlayer player, UUID token, SurveyReport report, boolean open) {
        PacketDistributor.sendToPlayer(
                player,
                new Report(
                        token,
                        player.serverLevel().dimension().location(),
                        JsonCodecs.survey(report),
                        open));
    }
}
