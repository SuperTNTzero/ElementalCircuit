package dev.elementalcircuits.block;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.RadianceResources;
import dev.elementalcircuits.server.WorldIllumination;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** Original source is encoded in chunk block state, so even an orphan can restore safely. */
public final class DimmedLightBlock extends Block {
    public enum Source implements StringRepresentable {
        TORCH(Blocks.TORCH),SOUL_TORCH(Blocks.SOUL_TORCH),REDSTONE_TORCH(Blocks.REDSTONE_TORCH),
        WALL_TORCH(Blocks.WALL_TORCH),SOUL_WALL_TORCH(Blocks.SOUL_WALL_TORCH),REDSTONE_WALL_TORCH(Blocks.REDSTONE_WALL_TORCH),
        GLOWSTONE(Blocks.GLOWSTONE),SEA_LANTERN(Blocks.SEA_LANTERN),SHROOMLIGHT(Blocks.SHROOMLIGHT),
        OCHRE_FROGLIGHT(Blocks.OCHRE_FROGLIGHT),VERDANT_FROGLIGHT(Blocks.VERDANT_FROGLIGHT),PEARLESCENT_FROGLIGHT(Blocks.PEARLESCENT_FROGLIGHT),
        JACK_O_LANTERN(Blocks.JACK_O_LANTERN),REDSTONE_LAMP(Blocks.REDSTONE_LAMP);
        public final Block block;Source(Block block){this.block=block;}
        @Override public String getSerializedName(){return name().toLowerCase(Locale.ROOT);}
    }
    public static final EnumProperty<Source> SOURCE=EnumProperty.create("source",Source.class);
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT=BlockStateProperties.LIT;
    public static final EnumProperty<Direction.Axis> AXIS=BlockStateProperties.AXIS;
    public DimmedLightBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(SOURCE,Source.TORCH).setValue(FACING,Direction.NORTH).setValue(LIT,true).setValue(AXIS,Direction.Axis.Y));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(SOURCE,FACING,LIT,AXIS);}
    public static BlockState mask(BlockState original){
        if(original.getLightEmission()==0 || original.hasBlockEntity())return null;
        for(Source source:Source.values())if(original.is(source.block)){
            BlockState state=ElementalCircuits.DIMMED_LIGHT.get().defaultBlockState().setValue(SOURCE,source);
            if(original.hasProperty(FACING))state=state.setValue(FACING,original.getValue(FACING));
            if(original.hasProperty(LIT))state=state.setValue(LIT,original.getValue(LIT));
            if(original.hasProperty(AXIS))state=state.setValue(AXIS,original.getValue(AXIS));
            return state;
        }
        return null;
    }
    public static BlockState original(BlockState masked){
        BlockState state=masked.getValue(SOURCE).block.defaultBlockState();
        if(state.hasProperty(FACING))state=state.setValue(FACING,masked.getValue(FACING));
        if(state.hasProperty(LIT))state=state.setValue(LIT,masked.getValue(LIT));
        if(state.hasProperty(AXIS))state=state.setValue(AXIS,masked.getValue(AXIS));
        return state;
    }
    private static boolean torch(BlockState state){return state.getValue(SOURCE).block instanceof TorchBlock;}
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        if(torch(state) && original(state).hasProperty(FACING))return WallTorchBlock.getShape(Blocks.WALL_TORCH.defaultBlockState().setValue(FACING,state.getValue(FACING)));
        return torch(state)?Block.box(6,0,6,10,10,10):Shapes.block();
    }
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return torch(state)?Shapes.empty():Shapes.block();}
    @Override public boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){return original(state).canSurvive(level,pos);}
    @Override public BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other){
        return !canSurvive(state,level,pos)?Blocks.AIR.defaultBlockState():super.updateShape(state,direction,neighbor,level,pos,other);
    }
    @Override public List<ItemStack> getDrops(BlockState state,LootParams.Builder params){return original(state).getDrops(params.withParameter(LootContextParams.BLOCK_STATE,original(state)));}
    @Override public ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state){return new ItemStack(original(state).getBlock());}
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){if(!level.isClientSide)level.scheduleTick(pos,this,RadianceResources.rules().orphanDelay());}
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        WorldIllumination.get(level).reconcile(level,pos);
        if(level.getBlockState(pos).is(this))level.scheduleTick(pos,this,RadianceResources.rules().orphanDelay());
    }
}
