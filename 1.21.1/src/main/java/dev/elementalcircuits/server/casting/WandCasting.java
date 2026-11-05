package dev.elementalcircuits.server.casting;

import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.CarrierRepository;
import dev.elementalcircuits.application.observation.SpellSound;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.CastGesture;
import dev.elementalcircuits.domain.circuit.SupplyMode;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.server.spell.SpellFeedback;
import dev.elementalcircuits.server.spell.SpellSounds;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Owns only the current native item-use gesture. Circuit mode and resources belong to the VM. */
public final class WandCasting {
    private record Session(
            ItemStack wand,
            ItemStack carrier,
            UUID identity,
            int slot,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
            CastGesture gesture) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private WandCasting() {}

    public static void begin(ServerPlayer p, ItemStack wand) {
        cancel(p);
        try {
            var carrier = CastingEquipment.carrier(p, wand);
            var a = CarrierRepository.load(carrier);
            if (a.boardBroken(a.selected()))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.casting._wand_casting.001"));
            var circuit = a.circuit(a.selected());
            boolean disconnect =
                    circuit.powered()
                            && SupplyMode.gesture(circuit.definition()) == SupplyMode.CONFIGURED;
            circuit.stop();
            dev.elementalcircuits.server.spell.WorldConnections.get(p.serverLevel())
                    .releaseSlot(a.identity(), a.selected());
            CarrierRepository.commit(carrier);
            if (disconnect) {
                p.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.casting._wand_casting.002")),
                        true);
                return;
            }
            SESSIONS.put(
                    p.getUUID(),
                    new Session(
                            wand,
                            carrier,
                            a.identity(),
                            a.selected(),
                            p.level().dimension(),
                            new CastGesture(
                                    ServerSettings.count("input.holdTicks"),
                                    ServerSettings.count("input.fullChargeTicks"),
                                    ServerSettings.mana("input.maximumCharge"))));
        } catch (RuntimeException e) {
            notice(p, e);
        }
    }

    private static boolean matches(ServerPlayer p, Session s) {
        return p.isAlive()
                && !p.isRemoved()
                && p.level().dimension() == s.dimension
                && p.getMainHandItem() == s.wand
                && CastingEquipment.carrier(p, s.wand) == s.carrier
                && CarrierRepository.load(s.carrier).identity().equals(s.identity)
                && CarrierRepository.load(s.carrier).selected() == s.slot;
    }

    public static void advance(ServerPlayer p, ItemStack wand, int elapsed) {
        var s = SESSIONS.get(p.getUUID());
        if (s == null || s.wand != wand) return;
        try {
            if (!matches(p, s)) {
                cancel(p);
                return;
            }
            if (s.gesture.advance(elapsed) == CastGesture.Action.CHARGE_STARTED)
                SpellSounds.observe(
                        p.serverLevel(), SpellSound.Kind.START, Element.PURE, p.position());
        } catch (RuntimeException e) {
            cancel(p);
            notice(p, e);
        }
    }

    public static void release(ServerPlayer p, ItemStack wand) {
        var s = SESSIONS.remove(p.getUUID());
        if (s == null || s.wand != wand) return;
        try {
            if (!matches(p, s)) return;
            if (s.gesture.release() != CastGesture.Action.RELEASE) return;
            if (CastingEquipment.broken(s.wand))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.casting._wand_casting.003"));
            var a = CarrierRepository.load(s.carrier);
            if (a.boardBroken(s.slot))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.casting._wand_casting.004"));
            a.reformCasting(ServerSettings.castingRules());
            var c = a.circuit(s.slot);
            c.stop();
            if (!c.start(SupplyMode.gesture(c.definition()), s.gesture.multiplier()))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.casting._wand_casting.005"));
            CarrierRepository.commit(s.carrier);
            SpellFeedback.started(p.serverLevel(), p.getEyePosition(), p.getLookAngle());
            SpellSounds.observe(p.serverLevel(), SpellSound.Kind.START, Element.PURE, p.position());
        } catch (RuntimeException e) {
            notice(p, e);
        }
    }

    public static void cancel(ServerPlayer p) {
        var s = SESSIONS.remove(p.getUUID());
        if (s != null) s.gesture.cancel();
    }

    public static void tick(ServerPlayer p) {
        var s = SESSIONS.get(p.getUUID());
        if (s == null) return;
        try {
            if (!p.isUsingItem() || p.getUseItem() != s.wand || !matches(p, s)) cancel(p);
        } catch (RuntimeException e) {
            cancel(p);
        }
    }

    private static void notice(ServerPlayer p, RuntimeException e) {
        p.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                        e.getMessage() == null
                                ? dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.casting._wand_casting.006")
                                : e.getMessage()),
                true);
    }
}
