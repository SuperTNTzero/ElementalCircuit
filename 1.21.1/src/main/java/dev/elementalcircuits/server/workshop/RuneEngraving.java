package dev.elementalcircuits.server.workshop;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.domain.circuit.*;
import dev.elementalcircuits.domain.combat.Element;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.registries.*;

import java.util.*;

public final class RuneEngraving {
    public static final DeferredHolder<Item, Item> DUST =
            ElementalCircuits.ITEMS.register(
                    "mana_dust", dev.elementalcircuits.item.ManaDustItem::new);
    public static final DeferredHolder<Block, Block> TABLE =
            ElementalCircuits.BLOCKS.register(
                    "rune_engraving_table",
                    () ->
                            new dev.elementalcircuits.block.RuneEngravingTable(
                                    net.minecraft.world.level.block.state.BlockBehaviour.Properties
                                            .ofFullCopy(Blocks.SPRUCE_PLANKS)
                                            .strength(2.5f)));
    public static final DeferredHolder<Item, Item> TABLE_ITEM =
            ElementalCircuits.ITEMS.register(
                    "rune_engraving_table",
                    () -> new BlockItem(TABLE.get(), new Item.Properties()));
    public static final Map<String, DeferredHolder<Item, Item>> RUNES;
    public static final List<String> KEYS;
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ElementalCircuits.ID);
    public static final DeferredHolder<MenuType<?>, MenuType<RuneEngravingMenu>> MENU =
            MENUS.register(
                    "rune_engraving",
                    () -> new MenuType<>(RuneEngravingMenu::new, FeatureFlags.DEFAULT_FLAGS));

    static {
        Map<String, DeferredHolder<Item, Item>> runes = new LinkedHashMap<>();
        for (var kind : Rune.Kind.values()) {
            if (kind == Rune.Kind.CONVERT) {
                for (var element : Element.values())
                    register(runes, RuneMaterials.key(kind, element));
            } else register(runes, RuneMaterials.key(kind, Element.PURE));
        }
        RUNES = Collections.unmodifiableMap(runes);
        KEYS = List.copyOf(runes.keySet());
    }

    private static void register(Map<String, DeferredHolder<Item, Item>> runes, String key) {
        runes.put(
                key, ElementalCircuits.ITEMS.register(key, () -> new Item(new Item.Properties())));
    }

    private RuneEngraving() {}
}
