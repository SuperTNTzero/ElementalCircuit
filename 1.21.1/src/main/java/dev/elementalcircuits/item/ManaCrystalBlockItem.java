package dev.elementalcircuits.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** Localized discovery hint only; amplification remains server-owned. */
public final class ManaCrystalBlockItem extends BlockItem {
    public ManaCrystalBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(
                Component.translatable("tooltip.elementalcircuits.crystal_support")
                        .withStyle(ChatFormatting.AQUA));
    }
}
