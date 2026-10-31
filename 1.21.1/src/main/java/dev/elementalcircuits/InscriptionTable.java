package dev.elementalcircuits;
import dev.elementalcircuits.block.*;
import dev.elementalcircuits.server.InscriptionAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
public final class InscriptionTable extends BaseEntityBlock {
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<BoardMaterial> BOARD=EnumProperty.create("board",BoardMaterial.class);
    public InscriptionTable(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(BOARD,BoardMaterial.EMPTY));}
    @Override protected com.mojang.serialization.MapCodec<InscriptionTable> codec(){return simpleCodec(InscriptionTable::new);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,BOARD);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override public RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Shapes.block();}
    @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return WorkstationGeometry.inscription(s.getValue(FACING));}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new WorkstationBlockEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return l.isClientSide?null:createTickerHelper(t,ElementalCircuits.WORKSTATION_ENTITY.get(),WorkstationBlockEntity::tick);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){if(hand==InteractionHand.MAIN_HAND && p instanceof ServerPlayer server)InscriptionAccess.interact(server,pos);return ItemInteractionResult.sidedSuccess(level.isClientSide);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player p,BlockHitResult hit){if(p instanceof ServerPlayer server)InscriptionAccess.interact(server,pos);return InteractionResult.sidedSuccess(level.isClientSide);}
}
