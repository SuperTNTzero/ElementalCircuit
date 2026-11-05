package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.persistence.ItemData;
import dev.elementalcircuits.application.observation.*;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.network.FeedbackNetwork;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/** Coalesces all sub-hits into at most one confirmation per owner per world boundary. */
public final class SpellFeedback {
    private static final Map<ServerLevel, Map<UUID, FeedbackNetwork.Pulse>> OUTBOX =
            new WeakHashMap<>();

    private SpellFeedback() {}

    public static boolean handCast(ServerLevel level, UUID owner, UUID carrier) {
        if (owner == null
                || !(level.getPlayerByUUID(owner)
                        instanceof net.minecraft.server.level.ServerPlayer player)
                || !player.getMainHandItem().is(ElementalCircuits.WAND.get())) return false;
        var stack =
                player.getOffhandItem().is(ElementalCircuits.SPELL_BOOK.get())
                        ? player.getOffhandItem()
                        : player.getMainHandItem();
        var data = ItemData.read(stack).getCompound("ElementalCircuit");
        return data.hasUUID("Identity") && data.getUUID("Identity").equals(carrier);
    }

    public static void observe(
            ServerLevel level,
            UUID owner,
            boolean cast,
            boolean hit,
            Element element,
            float power) {
        if (owner == null || level.getPlayerByUUID(owner) == null) return;
        var next = new FeedbackNetwork.Pulse(cast, hit, element, Math.max(.1f, Math.min(2, power)));
        OUTBOX.computeIfAbsent(level, l -> new HashMap<>())
                .merge(
                        owner,
                        next,
                        (a, b) ->
                                new FeedbackNetwork.Pulse(
                                        a.cast() || b.cast(),
                                        a.hit() || b.hit(),
                                        b.hit() ? b.element() : a.element(),
                                        Math.max(a.power(), b.power())));
    }

    public static FeedbackNetwork.Pulse pending(ServerLevel level, UUID owner) {
        var q = OUTBOX.get(level);
        return q == null ? null : q.get(owner);
    }

    public static void flush(ServerLevel level) {
        var q = OUTBOX.remove(level);
        if (q == null) return;
        for (var player : level.players()) {
            var p = q.get(player.getUUID());
            if (p != null) PacketDistributor.sendToPlayer(player, p);
        }
    }

    public static void unload(ServerLevel level) {
        OUTBOX.remove(level);
    }

    public static void started(ServerLevel level, Vec3 origin, Vec3 direction) {
        Vec3 normal = direction.normalize();
        if (normal.lengthSqr() < .5) normal = new Vec3(0, 0, 1);
        SpellVisuals.observe(
                level,
                SpellVisual.Kind.START,
                Element.PURE,
                origin.add(normal.scale(.85)),
                normal,
                .7f,
                1);
    }
}
