package dev.elementalcircuits.item;

import com.google.common.collect.Multimap;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/** Curios owns placement, synchronization and persistence of the physical ring. */
public final class CircuitRingItem extends Item implements ICurioItem {
    public CircuitRingItem() {
        super(new Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON));
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(
            SlotContext context, ResourceLocation id, ItemStack stack) {
        return dev.elementalcircuits.adapter.compat.CuriosEquipment.enchantmentAttributes(
                stack, id);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            java.util.List<net.minecraft.network.chat.Component> lines,
            net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(
                net.minecraft.network.chat.Component.translatable(
                                "tooltip.elementalcircuits.equipment_inscription")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
