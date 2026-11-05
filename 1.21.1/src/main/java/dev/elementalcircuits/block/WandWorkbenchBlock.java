package dev.elementalcircuits.block;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.server.workshop.WorkshopService;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
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

public final class WandWorkbenchBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;

    public WandWorkbenchBlock(Properties p) {
        super(p);
        registerDefaultState(
                stateDefinition
                        .any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(PART, BedPart.FOOT));
    }

    @Override
    protected com.mojang.serialization.MapCodec<WandWorkbenchBlock> codec() {
        return simpleCodec(WandWorkbenchBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, PART);
    }

    public static BlockPos master(BlockPos pos, BlockState s) {
        return s.getValue(PART) == BedPart.FOOT
                ? pos
                : pos.relative(s.getValue(FACING).getCounterClockWise());
    }

    public static BlockPos other(BlockPos pos, BlockState s) {
        return pos.relative(
                s.getValue(PART) == BedPart.FOOT
                        ? s.getValue(FACING).getClockWise()
                        : s.getValue(FACING).getCounterClockWise());
    }

    public static boolean complete(Level level, BlockPos pos) {
        var s = level.getBlockState(pos);
        if (!s.is(ElementalCircuits.WAND_WORKBENCH.get())) return false;
        var n = level.getBlockState(other(pos, s));
        return n.is(s.getBlock())
                && n.getValue(PART) != s.getValue(PART)
                && n.getValue(FACING) == s.getValue(FACING);
    }

    public static boolean ensureComplete(ServerPlayer player, BlockPos pos) {
        var l = player.serverLevel();
        var s = l.getBlockState(pos);
        if (!s.is(ElementalCircuits.WAND_WORKBENCH.get())) return false;
        if (complete(l, pos)) return true;
        var peer = other(pos, s);
        if (s.getValue(PART) != BedPart.FOOT
                || !player.isAlive()
                || !player.getAbilities().mayBuild
                || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 64
                || !l.mayInteract(player, pos)
                || !l.hasChunkAt(peer)
                || !l.getWorldBorder().isWithinBounds(peer)
                || !l.mayInteract(player, peer)
                || !l.getBlockState(peer).isAir()
                || !l.noCollision(
                        net.minecraft.world.phys.shapes.Shapes.block()
                                .move(peer.getX(), peer.getY(), peer.getZ())
                                .bounds())) return false;
        return l.setBlock(peer, s.setValue(PART, BedPart.HEAD), 3);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        var s = defaultBlockState().setValue(FACING, c.getHorizontalDirection());
        var other = other(c.getClickedPos(), s);
        return c.getLevel().getWorldBorder().isWithinBounds(other)
                        && c.getLevel().getBlockState(other).canBeReplaced(c)
                        && (c.getPlayer() == null || c.getLevel().mayInteract(c.getPlayer(), other))
                        && c.getLevel()
                                .noCollision(
                                        Shapes.block()
                                                .move(other.getX(), other.getY(), other.getZ())
                                                .bounds())
                ? s
                : null;
    }

    @Override
    public void setPlacedBy(
            Level l, BlockPos p, BlockState s, LivingEntity placer, ItemStack stack) {
        if (!l.isClientSide) l.setBlock(other(p, s), s.setValue(PART, BedPart.HEAD), 3);
    }

    @Override
    public void onRemove(BlockState s, Level l, BlockPos p, BlockState next, boolean moving) {
        if (!s.is(next.getBlock())) {
            var other = other(p, s);
            var n = l.getBlockState(other);
            if (n.is(this)
                    && n.getValue(PART) != s.getValue(PART)
                    && n.getValue(FACING) == s.getValue(FACING))
                l.setBlock(other, Blocks.AIR.defaultBlockState(), 3);
        }
        super.onRemove(s, l, p, next, moving);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Override
    public net.minecraft.world.level.material.PushReaction getPistonPushReaction(BlockState s) {
        return net.minecraft.world.level.material.PushReaction.BLOCK;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return s.getValue(PART) == BedPart.FOOT ? new WorkstationBlockEntity(p, s) : null;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level l, BlockState s, BlockEntityType<T> t) {
        return l.isClientSide
                ? null
                : createTickerHelper(
                        t,
                        ElementalCircuits.WORKSTATION_ENTITY.get(),
                        WorkstationBlockEntity::tick);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return WorkstationGeometry.desk(s.getValue(FACING), s.getValue(PART) == BedPart.HEAD);
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState s,
            Level l,
            BlockPos p,
            Player user,
            InteractionHand hand,
            BlockHitResult hit) {
        if (hand == InteractionHand.MAIN_HAND && user instanceof ServerPlayer server)
            WorkshopService.open(server, master(p, s));
        return ItemInteractionResult.sidedSuccess(l.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState s, Level l, BlockPos p, Player user, BlockHitResult hit) {
        if (user instanceof ServerPlayer server) WorkshopService.open(server, master(p, s));
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
