package dev.elementalcircuits.item;

import dev.elementalcircuits.adapter.persistence.ItemAppearance;
import dev.elementalcircuits.server.workshop.BookService;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import java.util.List;

public final class BlueprintNotebookItem extends Item {
    public BlueprintNotebookItem(Properties p) {
        super(p);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack a, ItemStack b, boolean changed) {
        return ItemAppearance.reequipBook(a, b, changed, true);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        if (!level.isClientSide && p instanceof ServerPlayer server)
            BookService.openNotebook(server, p.getItemInHand(hand));
        return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(
            ItemStack s, Item.TooltipContext context, List<Component> text, TooltipFlag f) {
        text.add(Component.translatable("tooltip.elementalcircuits.notebook_capacity"));
        text.add(Component.translatable("tooltip.elementalcircuits.notebook_share"));
    }
}
