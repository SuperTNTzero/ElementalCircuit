package dev.elementalcircuits.server.circuit;

import dev.elementalcircuits.adapter.persistence.ItemData;
import dev.elementalcircuits.domain.circuit.Rules;

import net.minecraft.world.item.ItemStack;

/** Small visual metadata avoids decoding a complete circuit every item-render frame. */
public final class FixedCircuitAppearance {
    private FixedCircuitAppearance() {}

    public static void update(ItemStack stack) {
        int material =
                CarrierService.assembly(stack).circuit(1).definition().rules().material().ordinal();
        var tag = ItemData.read(stack);
        if (!tag.contains("FixedMaterial", net.minecraft.nbt.Tag.TAG_INT)
                || tag.getInt("FixedMaterial") != material)
            ItemData.update(stack, t -> t.putInt("FixedMaterial", material));
    }

    public static Rules.Material material(ItemStack stack) {
        int value = ItemData.read(stack).getInt("FixedMaterial");
        return value >= 0 && value < 3 ? Rules.Material.values()[value] : Rules.Material.WOOD;
    }
}
