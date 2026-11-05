package dev.elementalcircuits.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

import java.util.List;

public final class ManaCrystalItem extends Item {
    public ManaCrystalItem(Properties p) {
        super(p);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(
                Component.translatable("tooltip.elementalcircuits.mana_crystal")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
