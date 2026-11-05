package dev.elementalcircuits.server.workshop;

import dev.elementalcircuits.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Rules;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;

import java.util.*;

/** Material inventory operations shared by editor commands and observations. */
public final class MaterialInventory {
    private MaterialInventory() {}

    public static Item plate(Rules.Material m) {
        return switch (m) {
            case WOOD -> ElementalCircuits.WOOD_PLATE.get();
            case BRASS -> ElementalCircuits.BRASS_PLATE.get();
            case CRYSTAL -> ElementalCircuits.CRYSTAL_PLATE.get();
        };
    }

    public static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            if (p.getInventory().getItem(i).is(item)) n += p.getInventory().getItem(i).getCount();
        return n;
    }

    public static int inventory(ServerPlayer p, Item item) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            if (p.getInventory().getItem(i).is(item)) return i;
        throw new IllegalArgumentException(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.server.workshop._material_inventory.001"));
    }

    public static void give(ServerPlayer p, ItemStack item) {
        if (!p.getInventory().add(item)) p.drop(item, false);
    }
}
