package dev.elementalcircuits.item;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.server.equipment.SpellAttributes;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** Native equipment attributes; circuit ownership is supplied by EquipmentCasting. */
public final class WizardArmorItem extends ArmorItem {
    private final java.util.function.Supplier<ItemAttributeModifiers> attributes =
            com.google.common.base.Suppliers.memoize(this::buildModifiers);

    public WizardArmorItem(Holder<ArmorMaterial> material, Type type) {
        super(material, type, new Item.Properties().durability(type.getDurability(18)));
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return attributes.get();
    }

    private ItemAttributeModifiers buildModifiers() {
        var b = ItemAttributeModifiers.builder();
        for (var entry : super.getDefaultAttributeModifiers().modifiers())
            b.add(entry.attribute(), entry.modifier(), entry.slot());
        var slot = EquipmentSlotGroup.bySlot(getEquipmentSlot());
        double weight =
                switch (getType()) {
                    case HELMET -> 1;
                    case CHESTPLATE -> 2;
                    case LEGGINGS -> 1.5;
                    default -> 1;
                };
        var id =
                ResourceLocation.fromNamespaceAndPath(
                        ElementalCircuits.ID, "wizard_" + getType().getName());
        b.add(
                SpellAttributes.POWER,
                new AttributeModifier(id, .10 * weight, AttributeModifier.Operation.ADD_VALUE),
                slot);
        b.add(
                SpellAttributes.MANA_CAPACITY,
                new AttributeModifier(id, 160 * weight, AttributeModifier.Operation.ADD_VALUE),
                slot);
        b.add(
                SpellAttributes.MANA_REGENERATION,
                new AttributeModifier(id, .5 * weight, AttributeModifier.Operation.ADD_VALUE),
                slot);
        b.add(
                SpellAttributes.MANA_REDUCTION,
                new AttributeModifier(id, 4 * weight, AttributeModifier.Operation.ADD_VALUE),
                slot);
        return b.build();
    }

    @Override
    public void appendHoverText(
            net.minecraft.world.item.ItemStack stack,
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
