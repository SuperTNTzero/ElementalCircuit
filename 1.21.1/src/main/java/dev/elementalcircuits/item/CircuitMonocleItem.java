package dev.elementalcircuits.item;

import com.google.common.collect.Multimap;

import dev.elementalcircuits.adapter.compat.CuriosEquipment;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.*;

import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

public final class CircuitMonocleItem extends ArmorItem implements ICurioItem {
    public CircuitMonocleItem(Holder<ArmorMaterial> material) {
        super(material, Type.HELMET, new Properties().durability(110));
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(
            SlotContext context, ResourceLocation id, ItemStack stack) {
        return CuriosEquipment.enchantmentAttributes(stack, id);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(
                Component.translatable("tooltip.elementalcircuits.relation_sight")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
