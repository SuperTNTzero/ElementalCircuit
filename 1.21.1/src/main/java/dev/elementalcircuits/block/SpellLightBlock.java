package dev.elementalcircuits.block;

import dev.elementalcircuits.adapter.resources.RadianceResources;
import dev.elementalcircuits.server.spell.WorldIllumination;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.shapes.*;

/**
 * Invisible real block light, no item or drops. Native scheduled ticks clean orphaned placements.
 */
public final class SpellLightBlock extends Block {
    public SpellLightBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(6, 6, 6, 10, 10, 10);
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public void onPlace(
            BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide)
            level.scheduleTick(pos, this, RadianceResources.rules().orphanDelay());
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        WorldIllumination.get(level).reconcile(level, pos);
        if (level.getBlockState(pos).is(this))
            level.scheduleTick(pos, this, RadianceResources.rules().orphanDelay());
    }
}
