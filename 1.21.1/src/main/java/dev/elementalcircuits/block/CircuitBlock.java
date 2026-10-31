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
    @Override protected com.mojang.serialization.MapCodec<CircuitBlock> codec(){return simpleCodec(CircuitBlock::new);}
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<BoardMaterial> MATERIAL=EnumProperty.create("material",BoardMaterial.class,m->m!=BoardMaterial.EMPTY);
    public CircuitBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(MATERIAL,BoardMaterial.WOOD));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,MATERIAL);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite()).setValue(MATERIAL,BoardMaterial.of(FixedCircuitAppearance.material(context.getItemInHand())));}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    private static final net.minecraft.world.phys.shapes.VoxelShape NORTH_SOUTH=net.minecraft.world.phys.shapes.Shapes.or(box(4,0,.4,12,3.52,15.6),box(2,0,2,14,3.52,14),box(1.4,0,4,14.6,3.52,12));
    private static final net.minecraft.world.phys.shapes.VoxelShape EAST_WEST=net.minecraft.world.phys.shapes.Shapes.or(box(.4,0,4,15.6,3.52,12),box(2,0,2,14,3.52,14),box(4,0,1.4,12,3.52,14.6));
    @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,net.minecraft.world.phys.shapes.CollisionContext c){return s.getValue(FACING).getAxis()==Direction.Axis.Z?NORTH_SOUTH:EAST_WEST;}
    @Override public PushReaction getPistonPushReaction(BlockState state){return PushReaction.BLOCK;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new CircuitBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,ElementalCircuits.CIRCUIT_ENTITY.get(),(l,p,s,b)->WorldCircuits.register(b));}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){if(!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof CircuitBlockEntity b)b.placed(stack,player);}
    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){interact(state,level,pos,player,hand,hit);return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return interact(state,level,pos,player,InteractionHand.MAIN_HAND,hit);}
    private InteractionResult interact(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!level.isClientSide && hand==InteractionHand.MAIN_HAND && player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof CircuitBlockEntity b){
            try{if(!b.allowed(server))throw new IllegalArgumentException("仅所有者可在8格内操作固定法阵");if(player.isShiftKeyDown() && player.getMainHandItem().is(ElementalCircuits.WAND.get()))InscriptionAccess.select(server,b);else if(player.isShiftKeyDown()){long paid=b.charge(server);player.displayClientMessage(net.minecraft.network.chat.Component.literal("已充入"+dev.elementalcircuits.domain.Fixed.display(paid)+"点魔力"),true);}else {var c=b.assembly().circuit(b.assembly().selected());if(c.powered())c.stop();else c.start();CarrierService.persist(b.carrier());b.setChanged();}}
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
    @Override public List<ItemStack> getDrops(BlockState state,LootParams.Builder builder){var be=builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);if(be instanceof CircuitBlockEntity b){dev.elementalcircuits.adapter.persistence.CarrierRepository.commit(b.carrier());FixedCircuitAppearance.update(b.carrier());return List.of(b.carrier().copy());}return List.of(new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get()));}
    @Override public ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state){var result=new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get());var material=switch(state.getValue(MATERIAL)){case BRASS->dev.elementalcircuits.domain.Rules.Material.BRASS;case CRYSTAL->dev.elementalcircuits.domain.Rules.Material.CRYSTAL;default->dev.elementalcircuits.domain.Rules.Material.WOOD;};var a=CarrierService.assembly(result);a.replace(1,dev.elementalcircuits.domain.Blueprint.empty(dev.elementalcircuits.adapter.RuleResources.defaults().withMaterial(material)));CarrierService.persist(result);FixedCircuitAppearance.update(result);return result;}
}
