package dev.elementalcircuits.adapter.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.elementalcircuits.ElementalCircuits;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Tiny native projection for tooltips and baked-model selection; never loads a VM. */
public record BoardAppearance(
        int slot, long remaining, long maximum, long capacity, boolean melted) {
    private static final MapCodec<BoardAppearance> CODEC =
            RecordCodecBuilder.<BoardAppearance>mapCodec(
                            i ->
                                    i.group(
                                                    Codec.INT
                                                            .fieldOf("Slot")
                                                            .forGetter(BoardAppearance::slot),
                                                    Codec.LONG
                                                            .fieldOf("Remaining")
                                                            .forGetter(BoardAppearance::remaining),
                                                    Codec.LONG
                                                            .fieldOf("Maximum")
                                                            .forGetter(BoardAppearance::maximum),
                                                    Codec.LONG
                                                            .fieldOf("Capacity")
                                                            .forGetter(BoardAppearance::capacity),
                                                    Codec.BOOL
                                                            .fieldOf("Melted")
                                                            .forGetter(BoardAppearance::melted))
                                            .apply(i, BoardAppearance::new))
                    .codec()
                    .fieldOf("BoardStatus");

    public static BoardAppearance read(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .read(CODEC)
                .result()
                .orElse(null);
    }

    public static BoardAppearance active(ItemStack wand, LivingEntity holder) {
        if (holder instanceof Player p && p.getMainHandItem() == wand) {
            var book = p.getOffhandItem();
            var source = ItemAppearance.spellSource(wand);
            if (book.is(ElementalCircuits.SPELL_BOOK.get())
                    && source != null
                    && source.equals(ItemAppearance.identity(book))) return read(book);
        }
        return read(wand);
    }
}
