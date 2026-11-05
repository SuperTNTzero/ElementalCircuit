package dev.elementalcircuits.server.equipment;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.item.CircuitRingItem;
import dev.elementalcircuits.item.WizardArmorItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

import java.util.*;

/** Registration and equipment category, independent of casting and presentation. */
public final class WizardEquipment {
    private static final DeferredRegister<ArmorMaterial> MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, ElementalCircuits.ID);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MATERIAL =
            MATERIALS.register(
                    "wizard",
                    () ->
                            new ArmorMaterial(
                                    Map.of(
                                            ArmorItem.Type.HELMET,
                                            2,
                                            ArmorItem.Type.CHESTPLATE,
                                            5,
                                            ArmorItem.Type.LEGGINGS,
                                            4,
                                            ArmorItem.Type.BOOTS,
                                            1),
                                    18,
                                    SoundEvents.ARMOR_EQUIP_LEATHER,
                                    () ->
                                            Ingredient.of(
                                                    Items.LEATHER,
                                                    ElementalCircuits.MANA_CRYSTAL.get()),
                                    List.of(
                                            new ArmorMaterial.Layer(
                                                    ResourceLocation.fromNamespaceAndPath(
                                                            ElementalCircuits.ID, "wizard"))),
                                    0,
                                    0));
    public static final DeferredHolder<Item, Item> HAT = armor("wizard_hat", ArmorItem.Type.HELMET);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MONOCLE_MATERIAL =
            MATERIALS.register(
                    "circuit_monocle",
                    () ->
                            new ArmorMaterial(
                                    Map.of(ArmorItem.Type.HELMET, 1),
                                    12,
                                    SoundEvents.ARMOR_EQUIP_GOLD,
                                    () -> Ingredient.of(Items.GOLD_INGOT),
                                    List.of(
                                            new ArmorMaterial.Layer(
                                                    ResourceLocation.fromNamespaceAndPath(
                                                            ElementalCircuits.ID,
                                                            "circuit_monocle"))),
                                    0,
                                    0));
    public static final DeferredHolder<Item, Item> MONOCLE =
            ElementalCircuits.ITEMS.register(
                    "circuit_monocle",
                    () -> new dev.elementalcircuits.item.CircuitMonocleItem(MONOCLE_MATERIAL));
    public static final DeferredHolder<Item, Item> ROBE =
            armor("wizard_robe", ArmorItem.Type.CHESTPLATE);
    public static final DeferredHolder<Item, Item> LEGGINGS =
            armor("wizard_leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredHolder<Item, Item> BOOTS =
            armor("wizard_boots", ArmorItem.Type.BOOTS);
    public static final DeferredHolder<Item, Item> RING =
            ElementalCircuits.ITEMS.register("circuit_ring", CircuitRingItem::new);

    private static DeferredHolder<Item, Item> armor(String name, ArmorItem.Type type) {
        return ElementalCircuits.ITEMS.register(name, () -> new WizardArmorItem(MATERIAL, type));
    }

    public static void register(IEventBus bus) {
        MATERIALS.register(bus);
    }

    public static boolean carrier(ItemStack stack) {
        return stack.getItem() instanceof WizardArmorItem
                || stack.getItem() instanceof CircuitRingItem;
    }

    private WizardEquipment() {}
}
