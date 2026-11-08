package dev.elementalcircuits.integration;

import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.RuneMaterials;
import dev.elementalcircuits.server.workshop.RuneEngraving;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Explicit survival ingredients for workstation tests; leaves room for tools and plates. */
final class RuneTestInventory {
    private RuneTestInventory() {}

    static void stock(ServerPlayer player, Blueprint blueprint) {
        int slot = 35;
        for (String key : RuneMaterials.counts(blueprint).keySet()) {
            player.getInventory().setItem(slot--, new ItemStack(RuneEngraving.RUNES.get(key).get(), 64));
        }
    }
}
