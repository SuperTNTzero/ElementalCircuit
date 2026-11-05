package dev.elementalcircuits.server.casting;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.persistence.CarrierRepository;
import dev.elementalcircuits.adapter.persistence.CooldownComponent;
import dev.elementalcircuits.adapter.persistence.SpellSelectionRepository;
import dev.elementalcircuits.adapter.resources.CooldownResources;
import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.server.enchantment.MagicEnchantments;
import dev.elementalcircuits.server.equipment.WandEquipment;
import dev.elementalcircuits.server.equipment.WizardEquipment;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Equipment binding is an execution capability, independent of notebook/spellbook UI commands. */
public final class CastingEquipment {
    private CastingEquipment() {}

    public static ItemStack carrier(ServerPlayer player, ItemStack wand) {
        var book = player.getOffhandItem();
        var selected = SpellSelectionRepository.source(wand);
        return player.getMainHandItem() == wand
                        && selected != null
                        && book.is(ElementalCircuits.SPELL_BOOK.get())
                        && CarrierRepository.identity(book).equals(selected)
                ? book
                : wand;
    }

    /** Revalidate physical ownership before changing either the slot or its source. */
    public static void select(ServerPlayer player, UUID source, int slot) {
        var wand = player.getMainHandItem();
        if (!wand.is(ElementalCircuits.WAND.get()))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.casting._casting_equipment.001"));
        var target = wand;
        if (!CarrierRepository.identity(wand).equals(source)) {
            target = player.getOffhandItem();
            if (!target.is(ElementalCircuits.SPELL_BOOK.get())
                    || !CarrierRepository.identity(target).equals(source))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.casting._casting_equipment.002"));
        }
        var assembly = CarrierRepository.load(target);
        assembly.select(slot);
        CarrierRepository.commit(target);
        SpellSelectionRepository.select(wand, source);
    }

    public static ItemStack wand(ServerPlayer player, ItemStack carrier) {
        if (!carrier.is(ElementalCircuits.SPELL_BOOK.get())) return carrier;
        if (player.getOffhandItem() != carrier
                || !player.getMainHandItem().is(ElementalCircuits.WAND.get()))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.casting._casting_equipment.003"));
        return player.getMainHandItem();
    }

    public static boolean broken(ItemStack stack) {
        return stack.is(ElementalCircuits.WAND.get()) && WandEquipment.broken(stack);
    }

    public static long strength(ItemStack stack) {
        return stack.is(ElementalCircuits.WAND.get())
                ? WandEquipment.stats(stack).strength()
                : Fixed.ONE;
    }

    public static boolean release(ItemStack stack) {
        return release(stack, null);
    }

    public static boolean release(ItemStack stack, ServerPlayer player) {
        long base = WizardEquipment.carrier(stack) ? Fixed.ONE : WandEquipment.stats(stack).speed();
        long speed =
                player == null ? base : MagicEnchantments.speed(player.serverLevel(), stack, base);
        if (!WizardEquipment.carrier(stack)) return WandCooldown.tryRelease(stack, speed);
        var current = WandCooldown.state(stack);
        if (!current.ready()) return false;
        stack.set(CooldownComponent.TYPE.get(), current.release(CooldownResources.rules(), speed));
        return true;
    }

    public static boolean pairedBookPowered(ServerPlayer player) {
        return player.getOffhandItem().is(ElementalCircuits.SPELL_BOOK.get())
                && CarrierRepository.load(player.getOffhandItem()).powered();
    }
}
