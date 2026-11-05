package dev.elementalcircuits.client.render;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.persistence.ItemData;
import dev.elementalcircuits.adapter.resources.WandResources;
import dev.elementalcircuits.domain.equipment.WandParts;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Immutable display stacks, never inventory objects. Uses the same baked model as held items. */
public final class WandPreview {
    private static final Map<WandParts.Assembly, ItemStack> CACHE = new HashMap<>();

    private WandPreview() {}

    public static ItemStack stack(WandParts.Assembly assembly) {
        assembly.stats(WandResources.definitions());
        return CACHE.computeIfAbsent(
                assembly,
                a -> {
                    ItemStack s = new ItemStack(ElementalCircuits.WAND.get());
                    CompoundTag t = new CompoundTag();
                    t.putInt("Version", 1);
                    t.putString("Head", a.head());
                    t.putString("Body", a.body());
                    t.putString("Tail", a.tail());
                    t.putLong("Remainder", 0);
                    ItemData.put(s, "WandAssembly", t);
                    return s;
                });
    }

    public static void render(GuiGraphics g, int x, int y, int size, WandParts.Assembly assembly) {
        g.pose().pushPose();
        g.pose().translate(x - size / 2.0, y, 0);
        g.pose().scale(size / 16f, size / 16f, 1);
        g.renderItem(stack(assembly), 0, 0);
        g.pose().popPose();
    }
}
