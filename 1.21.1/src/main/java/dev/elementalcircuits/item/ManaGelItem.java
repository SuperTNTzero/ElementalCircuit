package dev.elementalcircuits.item;

import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.server.mana.InstantMana;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import java.util.List;

public final class ManaGelItem extends Item {
    public ManaGelItem() {
        super(
                new Properties()
                        .food(
                                new FoodProperties.Builder()
                                        .nutrition(0)
                                        .saturationModifier(0)
                                        .alwaysEdible()
                                        .build()));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        if (!level.isClientSide)
            InstantMana.restore(user, ServerSettings.mana("ecology.gelRestore"));
        return super.finishUsingItem(stack, level, user);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(
                Component.translatable("tooltip.elementalcircuits.mana_gel")
                        .withStyle(net.minecraft.ChatFormatting.AQUA));
    }
}
