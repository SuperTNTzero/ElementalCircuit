package dev.elementalcircuits.server.circuit;

import dev.elementalcircuits.adapter.persistence.ItemData;

import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Fail closed while preserving original state; no reset or mana refill. */
public final class CarrierFaults {
    private CarrierFaults() {}

    public static void report(ItemStack stack, ServerPlayer player) {
        var root = ItemData.read(stack);
        boolean malformedRoot =
                root.contains("ElementalCircuit")
                        && !root.contains("ElementalCircuit", Tag.TAG_COMPOUND);
        var flags = malformedRoot ? root : root.getCompound("ElementalCircuit");
        String key = malformedRoot ? "ElementalCircuitFault" : "Corrupt";
        if (!flags.getBoolean(key)) {
            flags.putBoolean(key, true);
            if (!malformedRoot) root.put("ElementalCircuit", flags);
            ItemData.write(stack, root);
            if (player != null)
                player.sendSystemMessage(
                        Component.literal(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._carrier_faults.001")));
        }
    }
}
