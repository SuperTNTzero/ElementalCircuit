package dev.elementalcircuits.network;

import dev.elementalcircuits.client.effect.FeedbackClient;
import dev.elementalcircuits.domain.combat.Element;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Owner-only, disposable confirmation. Carries no damage or authority for clients to apply. */
public final class FeedbackNetwork {
    public record Pulse(boolean cast, boolean hit, Element element, float power)
            implements CustomPacketPayload {
        public static final Type<Pulse> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("elementalcircuits", "feedback"));

        public Pulse {
            if ((!cast && !hit)
                    || element == null
                    || !Float.isFinite(power)
                    || power <= 0
                    || power > 2)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.network._feedback_network.001"));
        }

        @Override
        public Type<Pulse> type() {
            return TYPE;
        }
    }

    private FeedbackNetwork() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                Pulse.TYPE,
                StreamCodec.of(
                        (b, p) -> {
                            b.writeBoolean(p.cast);
                            b.writeBoolean(p.hit);
                            b.writeEnum(p.element);
                            b.writeFloat(p.power);
                        },
                        b ->
                                new Pulse(
                                        b.readBoolean(),
                                        b.readBoolean(),
                                        b.readEnum(Element.class),
                                        b.readFloat())),
                (p, c) -> FeedbackClient.accept(p));
    }
}
