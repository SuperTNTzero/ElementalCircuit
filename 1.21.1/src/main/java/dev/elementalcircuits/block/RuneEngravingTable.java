package dev.elementalcircuits.block;

import dev.elementalcircuits.server.workshop.RuneEngravingMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class RuneEngravingTable extends Block {
    public RuneEngravingTable(Properties properties) {
        super(properties);
    }

    private void open(Player player, Level level, BlockPos pos) {
        if (!level.isClientSide && level.mayInteract(player, pos) && player.getAbilities().mayBuild)
            player.openMenu(
                    new SimpleMenuProvider(
                            (id, inventory, p) ->
                                    new RuneEngravingMenu(
                                            id, inventory, ContainerLevelAccess.create(level, pos)),
                            Component.translatable(
                                    "block.elementalcircuits.rune_engraving_table")));
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        open(player, level, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (hand == InteractionHand.MAIN_HAND) open(player, level, pos);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
