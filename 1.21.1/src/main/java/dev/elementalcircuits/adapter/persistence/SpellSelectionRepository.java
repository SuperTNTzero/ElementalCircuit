package dev.elementalcircuits.adapter.persistence;

import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** The wand remembers a physical source identity, never a copied circuit or resource pool. */
public final class SpellSelectionRepository {
    private static final String KEY = "SelectedSpellSource";

    private SpellSelectionRepository() {}

    public static UUID source(ItemStack wand) {
        return ItemAppearance.spellSource(wand);
    }

    public static void select(ItemStack wand, UUID source) {
        if (source == null)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.persistence._spell_selection_repository.001"));
        ItemData.update(wand, tag -> tag.putUUID(KEY, source));
    }
}
