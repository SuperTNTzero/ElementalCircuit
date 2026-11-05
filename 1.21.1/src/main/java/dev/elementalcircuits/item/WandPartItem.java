package dev.elementalcircuits.item;

import dev.elementalcircuits.domain.equipment.WandParts;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

import java.util.List;

public final class WandPartItem extends Item {
    private final WandParts.Part part;

    public WandPartItem(WandParts.Part part) {
        super(new Properties());
        this.part = part;
    }

    public WandParts.Part part() {
        return part;
    }

    @Override
    public void appendHoverText(
            ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        if (part.slot() == WandParts.Slot.HEAD)
            lines.add(
                    Component.translatable(
                            "tooltip.elementalcircuits.wand_part_head",
                            String.format(java.util.Locale.ROOT, "%.2f", part.strength() / 65536.0),
                            String.format(java.util.Locale.ROOT, "%.2f", part.speed() / 65536.0)));
        if (part.durability() > 0)
            lines.add(
                    Component.translatable(
                            "tooltip.elementalcircuits.wand_part_durability", part.durability()));
        if (part.slot() == WandParts.Slot.TAIL)
            lines.add(
                    Component.translatable(
                            "tooltip.elementalcircuits.wand_part_melee", part.melee()));
        lines.add(Component.translatable("tooltip.elementalcircuits.wand_part_install"));
    }
}
