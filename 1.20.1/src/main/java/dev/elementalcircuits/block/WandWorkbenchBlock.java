package dev.elementalcircuits.block;
import dev.elementalcircuits.server.WorkshopService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;
public final class WandWorkbenchBlock extends Block {
    public WandWorkbenchBlock(BlockBehaviour.Properties p){super(p);}
    @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){if(!l.isClientSide && p instanceof ServerPlayer server && hand==InteractionHand.MAIN_HAND)WorkshopService.open(server,pos);return InteractionResult.sidedSuccess(l.isClientSide);}
}
