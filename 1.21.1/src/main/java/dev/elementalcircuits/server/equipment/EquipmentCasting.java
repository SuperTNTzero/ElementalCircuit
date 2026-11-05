package dev.elementalcircuits.server.equipment;

import dev.elementalcircuits.adapter.compat.CuriosEquipment;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.network.EquipmentNetwork;
import dev.elementalcircuits.server.casting.WandCooldown;
import dev.elementalcircuits.server.circuit.CarrierFaults;
import dev.elementalcircuits.server.circuit.WorldCircuits;
import dev.elementalcircuits.server.spell.ElementalDamageSource;
import dev.elementalcircuits.server.spell.SpellFeedback;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Physical equipped hosts, authoritative selection and explicit activation policy. */
public final class EquipmentCasting {
    public enum Trigger {
        MANUAL,
        HURT
    }

    public record Entry(
            UUID identity,
            int slot,
            String item,
            String material,
            boolean selected,
            Trigger trigger,
            int cooldown) {}

    private static final EquipmentSlot[] ARMOR = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final Map<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> LAST_VIEW = new WeakHashMap<>();
    private static final String SELECTION = "ElementalEquipmentSelection",
            TRIGGERS = "EquipmentTriggers";

    private EquipmentCasting() {}

    public static List<ItemStack> equipped(ServerPlayer p) {
        List<ItemStack> result = new ArrayList<>();
        for (var slot : ARMOR) {
            var s = p.getItemBySlot(slot);
            if (WizardEquipment.carrier(s)) result.add(s);
        }
        for (var s : CuriosEquipment.rings(p)) if (result.size() < 32) result.add(s);
        return List.copyOf(result);
    }

    public static boolean worn(ServerPlayer p, ItemStack s) {
        return equipped(p).stream().anyMatch(i -> i == s);
    }

    public static ItemStack owned(ServerPlayer p, UUID id) {
        ItemStack found = ItemStack.EMPTY;
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ItemStack> stacks = new ArrayList<>(equipped(p));
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            stacks.add(p.getInventory().getItem(i));
        for (var s : stacks)
            if (WizardEquipment.carrier(s) && seen.add(s)) {
                UUID actual;
                try {
                    actual = CarrierRepository.identity(s);
                } catch (RuntimeException e) {
                    CarrierFaults.report(s, p);
                    continue;
                }
                if (actual.equals(id)) {
                    if (!found.isEmpty())
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.equipment._equipment_casting.001"));
                    found = s;
                }
            }
        return found;
    }

    public static Trigger trigger(ItemStack s, int slot) {
        if (!ItemData.contains(s, TRIGGERS)) return Trigger.MANUAL;
        var tag = ItemData.section(s, TRIGGERS);
        if (!tag.contains("Version", net.minecraft.nbt.Tag.TAG_INT)
                || tag.getInt("Version") != 1
                || tag.contains("Slot" + slot)
                        && !tag.contains("Slot" + slot, net.minecraft.nbt.Tag.TAG_STRING))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.equipment._equipment_casting.002"));
        String name = tag.getString("Slot" + slot);
        return name.isEmpty() ? Trigger.MANUAL : Trigger.valueOf(name);
    }

    public static void trigger(ItemStack s, int slot, Trigger trigger) {
        CarrierRepository.load(s).circuit(slot);
        ItemData.update(
                s,
                t -> {
                    var v = t.getCompound(TRIGGERS).copy();
                    v.putInt("Version", 1);
                    v.putString("Slot" + slot, trigger.name());
                    t.put(TRIGGERS, v);
                });
    }

    public static List<Entry> inspect(ServerPlayer p) {
        var selected = p.getPersistentData().getCompound(SELECTION);
        List<Entry> entries = new ArrayList<>();
        for (var s : equipped(p))
            try {
                var a = CarrierRepository.load(s);
                for (int slot : a.slots())
                    entries.add(
                            new Entry(
                                    a.identity(),
                                    slot,
                                    s.getItem().getDescriptionId(),
                                    a.circuit(slot).definition().rules().material().name(),
                                    selected.hasUUID("Identity")
                                            && selected.getUUID("Identity").equals(a.identity())
                                            && selected.getInt("Slot") == slot,
                                    trigger(s, slot),
                                    WandCooldown.state(s).remaining()));
            } catch (RuntimeException e) {
                CarrierFaults.report(s, p);
            }
        return List.copyOf(entries);
    }

    public static void request(ServerPlayer p, UUID id, int slot, int action) {
        if (!p.isAlive() || p.isSpectator()) return;
        long now = p.serverLevel().getGameTime();
        if (action != 0 && LAST_REQUEST.getOrDefault(p, -100L) == now) return;
        if (action != 0) LAST_REQUEST.put(p, now);
        try {
            if (action == 0) {
                if (LAST_VIEW.getOrDefault(p, -100L) == now) return;
                LAST_VIEW.put(p, now);
                EquipmentNetwork.sync(p);
                return;
            }
            ItemStack s = ItemStack.EMPTY;
            if (action == 4) {
                var selection = p.getPersistentData().getCompound(SELECTION);
                UUID chosen = selection.hasUUID("Identity") ? selection.getUUID("Identity") : null;
                var rings = CuriosEquipment.rings(p);
                s =
                        rings.stream()
                                .filter(r -> CarrierRepository.identity(r).equals(chosen))
                                .findFirst()
                                .orElse(rings.isEmpty() ? ItemStack.EMPTY : rings.getFirst());
                if (!s.isEmpty()) slot = CarrierRepository.load(s).selected();
            } else if (action == 1 && id.equals(new UUID(0, 0))) {
                var selected = p.getPersistentData().getCompound(SELECTION);
                if (!selected.hasUUID("Identity"))
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.equipment._equipment_casting.003"));
                s = owned(p, selected.getUUID("Identity"));
                slot = selected.getInt("Slot");
            } else s = owned(p, id);
            if (s.isEmpty() || !worn(p, s))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.equipment._equipment_casting.004"));
            var a = CarrierRepository.load(s);
            a.circuit(slot);
            switch (action) {
                case 1, 4 -> {
                    if (trigger(s, slot) != Trigger.MANUAL)
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.equipment._equipment_casting.005"));
                    if (!activate(p, s, slot, p.getLookAngle()))
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.equipment._equipment_casting.006"));
                }
                case 2 -> {
                    a.select(slot);
                    CarrierRepository.commit(s);
                    var value = new CompoundTag();
                    value.putInt("Version", 1);
                    value.putUUID("Identity", a.identity());
                    value.putInt("Slot", slot);
                    p.getPersistentData().put(SELECTION, value);
                    p.displayClientMessage(
                            net.minecraft.network.chat.Component.literal(
                                            dev.elementalcircuits.text.Texts.get(
                                                    "text.elementalcircuits.server.equipment._equipment_casting.007"))
                                    .append(s.getHoverName())
                                    .append(
                                            dev.elementalcircuits.text.Texts.get(
                                                            "text.elementalcircuits.server.equipment._equipment_casting.008")
                                                    + slot),
                            true);
                }
                case 3 -> {
                    trigger(
                            s,
                            slot,
                            trigger(s, slot) == Trigger.MANUAL ? Trigger.HURT : Trigger.MANUAL);
                    a.circuit(slot).stop();
                    CarrierRepository.commit(s);
                }
                default ->
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.equipment._equipment_casting.009"));
            }
            EquipmentNetwork.sync(p);
        } catch (RuntimeException e) {
            p.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            e.getMessage() == null
                                    ? dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.server.equipment._equipment_casting.010")
                                    : e.getMessage()),
                    true);
        }
    }

    public static boolean activate(ServerPlayer p, ItemStack s, int slot, Vec3 direction) {
        if (!worn(p, s) || !WandCooldown.state(s).ready()) return false;
        var a = CarrierRepository.load(s);
        var c = a.circuit(slot);
        if (a.active() || !c.start()) return false;
        ItemData.update(
                s,
                t -> {
                    t.putBoolean("ReactiveCast", trigger(s, slot) == Trigger.HURT);
                    t.putDouble("CastAimX", direction.x);
                    t.putDouble("CastAimY", direction.y);
                    t.putDouble("CastAimZ", direction.z);
                });
        CarrierRepository.commit(s);
        WorldCircuits.enqueue(s, p);
        SpellFeedback.started(p.serverLevel(), p.getEyePosition(), direction);
        return true;
    }

    public static Vec3 direction(ServerPlayer p, ItemStack s) {
        var t = ItemData.read(s);
        var v = new Vec3(t.getDouble("CastAimX"), t.getDouble("CastAimY"), t.getDouble("CastAimZ"));
        return Double.isFinite(v.lengthSqr()) && v.lengthSqr() > .001
                ? v.normalize()
                : p.getLookAngle();
    }

    public static void tick(ServerPlayer p) {
        if (!p.isAlive()) return;
        var worn = equipped(p);
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var s : worn) if (seen.add(s)) tickStack(p, s, true);
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            var s = p.getInventory().getItem(i);
            if (WizardEquipment.carrier(s) && seen.add(s)) tickStack(p, s, false);
        }
    }

    private static void tickStack(ServerPlayer p, ItemStack s, boolean worn) {
        try {
            WandCooldown.tick(s);
            var a = CarrierRepository.load(s);
            if (!worn && a.powered()) {
                a.stopAll();
                CarrierRepository.commit(s);
            }
            if (a.active()) WorldCircuits.enqueue(s, p);
        } catch (RuntimeException e) {
            CarrierFaults.report(s, p);
        }
    }

    public static void hurt(
            ServerPlayer p, net.minecraft.world.damagesource.DamageSource source, float damage) {
        if (source instanceof ElementalDamageSource elemental && elemental.reactive()) return;
        if (damage <= 0 || !p.isAlive() || p.isSpectator()) return;
        var attacker = source.getEntity();
        var direction =
                attacker == null
                        ? p.getLookAngle()
                        : attacker.getEyePosition().subtract(p.getEyePosition()).normalize();
        for (var s : equipped(p))
            try {
                var a = CarrierRepository.load(s);
                var order = new ArrayList<>(a.slots());
                order.remove(Integer.valueOf(a.selected()));
                order.addFirst(a.selected());
                for (int slot : order)
                    if (trigger(s, slot) == Trigger.HURT && activate(p, s, slot, direction)) break;
            } catch (RuntimeException e) {
                CarrierFaults.report(s, p);
            }
    }
}
