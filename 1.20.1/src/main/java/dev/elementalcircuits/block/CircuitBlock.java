package dev.elementalcircuits.block;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.server.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import java.util.*;

public final class CircuitBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public CircuitBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public PushReaction getPistonPushReaction(BlockState state){return PushReaction.BLOCK;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new CircuitBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,ElementalCircuits.CIRCUIT_ENTITY.get(),(l,p,s,b)->WorldCircuits.register(b));}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){if(!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof CircuitBlockEntity b)b.placed(stack,player);}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!level.isClientSide && hand==InteractionHand.MAIN_HAND && player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof CircuitBlockEntity b){
            try{if(!b.allowed(server))throw new IllegalArgumentException("仅所有者可在8格内操作固定法阵");if(player.isShiftKeyDown()){long paid=b.charge(server);player.displayClientMessage(net.minecraft.network.chat.Component.literal("已充入"+dev.elementalcircuits.domain.Fixed.display(paid)+"点魔力"),true);}else CarrierService.open(server,b);}
            catch(RuntimeException e){player.displayClientMessage(net.minecraft.network.chat.Component.literal(e.getMessage()==null?"固定法阵数据异常":e.getMessage()),true);}
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(!state.is(next.getBlock()) && level instanceof net.minecraft.server.level.ServerLevel server && level.getBlockEntity(pos) instanceof CircuitBlockEntity block)try{
            WorldCircuitLinks.get(server).removed(block.assembly().identity(),pos,block.owner());
        }catch(RuntimeException error){org.slf4j.LoggerFactory.getLogger("ElementalCircuits").error("无法记录销毁的法阵锚点：{}",error.getMessage());}
        super.onRemove(state,level,pos,next,moving);
    }
    @Override public List<ItemStack> getDrops(BlockState state,LootParams.Builder builder){var be=builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);if(be instanceof CircuitBlockEntity b){dev.elementalcircuits.adapter.persistence.CarrierRepository.commit(b.carrier());return List.of(b.carrier().copy());}return List.of(new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get()));}
    @Override public ItemStack getCloneItemStack(BlockGetter level,BlockPos pos,BlockState state){return new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get());}
}
