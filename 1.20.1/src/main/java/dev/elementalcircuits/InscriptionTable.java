package dev.elementalcircuits;

import dev.elementalcircuits.server.CarrierService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;

public final class InscriptionTable extends Block {
    public InscriptionTable(BlockBehaviour.Properties properties){super(properties);}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!level.isClientSide && player instanceof ServerPlayer server && hand==InteractionHand.MAIN_HAND)CarrierService.open(server);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
