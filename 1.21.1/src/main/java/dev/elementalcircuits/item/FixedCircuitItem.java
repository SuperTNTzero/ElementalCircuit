package dev.elementalcircuits.item;

import dev.elementalcircuits.server.circuit.FixedCircuitAppearance;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;

public final class FixedCircuitItem extends BlockItem {
    public FixedCircuitItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(
                "block.elementalcircuits.fixed_circuit."
                        + FixedCircuitAppearance.material(stack)
                                .name()
                                .toLowerCase(java.util.Locale.ROOT));
    }
}
