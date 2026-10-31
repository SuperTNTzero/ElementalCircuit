package dev.elementalcircuits.adapter.persistence;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.function.Consumer;

/** Immutable component boundary: every read is a detached copy, every write replaces the component. */
public final class ItemData {
    private ItemData(){}
    public static CompoundTag read(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    public static void write(ItemStack stack,CompoundTag data){stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));}
    public static void update(ItemStack stack,Consumer<CompoundTag> change){var data=read(stack);change.accept(data);write(stack,data);}
    public static void put(ItemStack stack,String key,net.minecraft.nbt.Tag value){update(stack,t->t.put(key,value));}
    public static void remove(ItemStack stack,String key){update(stack,t->t.remove(key));}
}
