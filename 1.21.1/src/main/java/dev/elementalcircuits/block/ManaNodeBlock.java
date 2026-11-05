package dev.elementalcircuits.block;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.server.mana.WorldManaField;

import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public final class ManaNodeBlock extends BaseEntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public ManaNodeBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected com.mojang.serialization.MapCodec<ManaNodeBlock> codec() {
        return simpleCodec(ManaNodeBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        return state.is(ElementalCircuits.AERIAL_MANA_NODE.get())
                ? box(4, 4, 4, 12, 12, 12)
                : super.getShape(state, level, pos, context);
    }

    @Override
    public void animateTick(
            BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (!state.is(ElementalCircuits.AERIAL_MANA_NODE.get()) || !state.getValue(LIT)) return;
        if (random.nextInt(4) == 0)
            level.addParticle(
                    net.minecraft.core.particles.ParticleTypes.END_ROD,
                    pos.getX() + .5 + (random.nextDouble() - .5) * .7,
                    pos.getY() + .5 + (random.nextDouble() - .5) * .7,
                    pos.getZ() + .5 + (random.nextDouble() - .5) * .7,
                    0,
                    .005,
                    0);
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ManaNodeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? null
                : createTickerHelper(
                        type,
                        ElementalCircuits.MANA_NODE_ENTITY.get(),
                        (l, p, s, b) -> WorldManaField.get((ServerLevel) l).register(b));
    }

    @Override
    public void setPlacedBy(
            Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ManaNodeBlockEntity node)
            node.placed(stack);
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
        if (!stack.is(ElementalCircuits.MANA_CRYSTAL.get())
                || state.is(ElementalCircuits.NATURAL_MANA_NODE.get())
                || state.is(ElementalCircuits.AERIAL_MANA_NODE.get()))
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        interact(level, pos, player, hand);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    private void interact(Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server)
                || hand != InteractionHand.MAIN_HAND
                || !(level.getBlockEntity(pos) instanceof ManaNodeBlockEntity node)) return;
        if (!player.getAbilities().mayBuild
                || !level.mayInteract(player, pos)
                || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 64) return;
        try {
            ItemStack held = player.getItemInHand(hand);
            if (held.is(ElementalCircuits.MANA_CRYSTAL.get()) && !node.natural()) {
                if (!node.insert())
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.block._mana_node_block.001"));
                held.shrink(1);
            }
            player.displayClientMessage(
                    Component.literal(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.block._mana_node_block.002")),
                    true);
        } catch (RuntimeException error) {
            player.displayClientMessage(
                    Component.literal(
                            error.getMessage() == null
                                    ? dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.block._mana_node_block.003")
                                    : error.getMessage()),
                    true);
        }
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof ManaNodeBlockEntity node) return List.of(node.drop());
        return (state.is(ElementalCircuits.NATURAL_MANA_NODE.get())
                        || state.is(ElementalCircuits.AERIAL_MANA_NODE.get()))
                ? List.of()
                : List.of(new ItemStack(ElementalCircuits.MANA_NODE_ITEM.get()));
    }
}
