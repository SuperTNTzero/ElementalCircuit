package dev.elementalcircuits.adapter.compat;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import dev.elementalcircuits.item.CircuitMonocleItem;
import dev.elementalcircuits.item.CircuitRingItem;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import top.theillusivec4.curios.api.CuriosApi;

import java.util.*;

/** The single Curios inventory boundary. Cosmetic slots never become spell hosts. */
public final class CuriosEquipment {
    public static List<ItemStack> rings(LivingEntity wearer) {
        return CuriosApi.getCuriosInventory(wearer)
                .map(
                        h ->
                                h.findCurios(s -> s.getItem() instanceof CircuitRingItem).stream()
                                        .map(r -> r.stack())
                                        .toList())
                .orElse(List.of());
    }

    public static List<ItemStack> enchantedAccessories(LivingEntity wearer) {
        var result = new ArrayList<>(rings(wearer));
        CuriosApi.getCuriosInventory(wearer)
                .ifPresent(
                        h ->
                                h.findCurios("head").stream()
                                        .filter(
                                                r ->
                                                        !r.slotContext().cosmetic()
                                                                && r.stack().getItem()
                                                                        instanceof
                                                                        CircuitMonocleItem)
                                        .forEach(r -> result.add(r.stack())));
        return result;
    }

    /** Slot-specific IDs allow two accessories to contribute without overwriting one another. */
    public static Multimap<Holder<Attribute>, AttributeModifier> enchantmentAttributes(
            ItemStack stack, ResourceLocation id) {
        var result = LinkedHashMultimap.<Holder<Attribute>, AttributeModifier>create();
        EnchantmentHelper.forEachModifier(
                stack,
                EquipmentSlot.MAINHAND,
                (attribute, modifier) ->
                        result.put(
                                attribute,
                                new AttributeModifier(
                                        id.withSuffix(
                                                "/enchantment/"
                                                        + modifier.id().getNamespace()
                                                        + "/"
                                                        + modifier.id().getPath()),
                                        modifier.amount(),
                                        modifier.operation())));
        return result;
    }

    public static boolean monocleWorn(LivingEntity wearer) {
        if (wearer.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof CircuitMonocleItem)
            return true;
        return CuriosApi.getCuriosInventory(wearer)
                .map(
                        h ->
                                h.findCurios("head").stream()
                                        .anyMatch(
                                                r ->
                                                        !r.slotContext().cosmetic()
                                                                && r.stack().getItem()
                                                                        instanceof
                                                                        CircuitMonocleItem))
                .orElse(false);
    }

    private CuriosEquipment() {}
}
