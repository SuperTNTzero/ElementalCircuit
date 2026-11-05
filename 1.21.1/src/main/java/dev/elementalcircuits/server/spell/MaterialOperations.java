package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.block.CircuitBlockEntity;
import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.casting.MaterialPorts;
import dev.elementalcircuits.server.circuit.WorldCircuits;

import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.items.*;

import java.util.*;

/**
 * Server-only material transactions. Probes never extract; a rejected commit never creates items.
 */
final class MaterialOperations {
    private record Supply(IItemHandler inventory, int slot, ItemStack item, BlockState after) {}

    private MaterialOperations() {}

    private static ServerPlayer actor(ServerLevel level, UUID owner, BlockPos pos) {
        if (ServerSettings.count("automation.enabled") == 0
                || owner == null
                || !CastPermissions.mayChangeBlock(level, owner, pos)) return null;
        return level.getEntity(owner) instanceof ServerPlayer player ? player : null;
    }

    static long breakCost(ServerLevel level, BlockPos pos, BlockState state) {
        float hardness = state.getDestroySpeed(level, pos);
        if (state.isAir()
                || !state.getFluidState().isEmpty()
                || state.hasBlockEntity()
                || !Float.isFinite(hardness)
                || hardness < 0
                || hardness > ServerSettings.get("automation.maxHardness")) return 0;
        long cost =
                Math.max(
                        1,
                        Math.round(
                                (ServerSettings.get("automation.breakBase")
                                                + hardness
                                                        * ServerSettings.get(
                                                                "automation.hardnessCost"))
                                        * Fixed.ONE));
        // A legal configuration may quote more than one packet can ever pay. It is unavailable,
        // not a malformed world contact and never a reason to trip the owning circuit.
        return cost > Fixed.MAX ? 0 : cost;
    }

    private static IItemHandler inventory(
            ServerLevel level, UUID owner, MaterialPorts ports, int side) {
        if (side < 0 || ports.anchor() == null) return null;
        CircuitBlockEntity anchor = WorldCircuits.block(level, ports.anchor());
        if (anchor == null || !anchor.owner().equals(owner)) return null;
        Direction direction = Direction.from3DDataValue(side);
        BlockPos pos = anchor.getBlockPos().relative(direction);
        if (actor(level, owner, anchor.getBlockPos()) == null || actor(level, owner, pos) == null)
            return null;
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, direction.getOpposite());
    }

    private static Supply supply(
            ServerLevel level, UUID owner, BlockPos pos, Direction face, MaterialPorts ports) {
        ServerPlayer player = actor(level, owner, pos);
        if (player == null || !level.getBlockState(pos).isAir()) return null;
        IItemHandler inventory = inventory(level, owner, ports, ports.input());
        if (inventory == null) return null;
        int slots =
                Math.min(
                        inventory.getSlots(), ServerSettings.count("automation.maxContainerSlots"));
        for (int slot = 0; slot < slots; slot++) {
            ItemStack sample = inventory.extractItem(slot, 1, true);
            // Single-cell solid block items only. Stateful machines and multi-block items require
            // their own transaction adapter; never discard their components or place half a door.
            if (sample.isEmpty()
                    || !matches(sample, ports.filter())
                    || sample.getItem().getClass() != BlockItem.class
                    || sample.has(DataComponents.BLOCK_ENTITY_DATA)
                    || sample.has(DataComponents.CONTAINER)) continue;
            BlockItem item = (BlockItem) sample.getItem();
            var context =
                    new BlockPlaceContext(
                            level,
                            player,
                            InteractionHand.MAIN_HAND,
                            sample,
                            new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false));
            BlockState next = item.getBlock().getStateForPlacement(context);
            if (next == null || next.hasBlockEntity()) continue;
            next =
                    sample.getOrDefault(
                                    DataComponents.BLOCK_STATE,
                                    net.minecraft.world.item.component.BlockItemStateProperties
                                            .EMPTY)
                            .apply(next);
            if (!next.isCollisionShapeFullBlock(level, pos)
                    || !next.canSurvive(level, pos)
                    || !level.isUnobstructed(
                            next, pos, net.minecraft.world.phys.shapes.CollisionContext.empty()))
                continue;
            return new Supply(inventory, slot, sample.copyWithCount(1), next);
        }
        return null;
    }

    static BlockState placement(
            ServerLevel level, UUID owner, BlockPos pos, Direction face, MaterialPorts ports) {
        Supply supply = supply(level, owner, pos, face, ports);
        return supply == null ? null : supply.after();
    }

    static void place(
            ServerLevel level,
            UUID owner,
            BlockPos pos,
            BlockState before,
            BlockState after,
            Direction face,
            MaterialPorts ports) {
        Supply supply = supply(level, owner, pos, face, ports);
        if (supply == null || !supply.after().equals(after)) return;
        ServerPlayer player = actor(level, owner, pos);
        if (NeoForge.EVENT_BUS
                .post(
                        new ElementBlockChangeEvent(
                                BlockSnapshot.create(level.dimension(), level, pos),
                                level.getBlockState(pos.relative(face.getOpposite())),
                                player,
                                after))
                .isCanceled()) return;
        if (actor(level, owner, pos) == null || !level.getBlockState(pos).equals(before)) return;
        // Re-resolve after callbacks: the anchor/container may have been removed or replaced.
        supply = supply(level, owner, pos, face, ports);
        if (supply == null || !supply.after().equals(after)) return;
        ItemStack paid = supply.inventory().extractItem(supply.slot(), 1, false);
        if (paid.isEmpty()) return;
        if (paid.getCount() != 1
                || !ItemStack.isSameItemSameComponents(paid, supply.item())
                || actor(level, owner, pos) == null
                || !level.getBlockState(pos).equals(before)
                || !level.setBlock(pos, after, 3)) {
            returnOrDrop(level, pos, supply.inventory(), paid);
            return;
        }
        // A real item is consumed even for a creative owner: automation never duplicates stock.
        after.getBlock().setPlacedBy(level, pos, after, player, paid);
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, after));
    }

    static void dismantle(
            ServerLevel level, UUID owner, BlockPos pos, BlockState before, MaterialPorts ports) {
        ServerPlayer player = actor(level, owner, pos);
        if (player == null || breakCost(level, pos, before) == 0) return;
        if (NeoForge.EVENT_BUS
                .post(new BlockEvent.BreakEvent(level, pos, before, player))
                .isCanceled()) return;
        if (actor(level, owner, pos) == null || !level.getBlockState(pos).equals(before)) return;
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        var drops = new ArrayList<ItemEntity>();
        boolean loot = level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
        if (loot)
            for (ItemStack stack : Block.getDrops(before, level, pos, null, player, tool))
                drops.add(
                        new ItemEntity(
                                level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, stack));
        if (actor(level, owner, pos) == null
                || !level.getBlockState(pos).equals(before)
                || !level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)) return;
        level.levelEvent(2001, pos, Block.getId(before));
        level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, before));
        if (!loot) return;
        var event =
                NeoForge.EVENT_BUS.post(
                        new BlockDropsEvent(level, pos, before, null, drops, player, tool));
        if (event.isCanceled()) return;
        IItemHandler target = inventory(level, owner, ports, ports.output());
        for (ItemEntity drop : event.getDrops()) {
            ItemStack left =
                    matches(drop.getItem(), ports.filter())
                            ? insert(target, drop.getItem())
                            : drop.getItem();
            if (!left.isEmpty()) {
                drop.setItem(left);
                drop.setDefaultPickUpDelay();
                level.addFreshEntity(drop);
            }
        }
        before.spawnAfterBreak(level, pos, tool, false);
        if (event.getDroppedExperience() > 0)
            net.minecraft.world.entity.ExperienceOrb.award(
                    level, Vec3.atCenterOf(pos), event.getDroppedExperience());
    }

    private static ItemStack insert(IItemHandler inventory, ItemStack offered) {
        ItemStack left = offered.copy();
        if (inventory != null) {
            int slots =
                    Math.min(
                            inventory.getSlots(),
                            ServerSettings.count("automation.maxContainerSlots"));
            for (int slot = 0; slot < slots && !left.isEmpty(); slot++)
                left = inventory.insertItem(slot, left, false);
        }
        return left;
    }

    private static boolean matches(ItemStack stack, String filter) {
        if (filter == null || filter.isEmpty()) return true;
        if (filter.startsWith("#"))
            return stack.is(
                    TagKey.create(
                            Registries.ITEM,
                            net.minecraft.resources.ResourceLocation.parse(filter.substring(1))));
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(filter);
    }

    private static void returnOrDrop(
            ServerLevel level, BlockPos pos, IItemHandler inventory, ItemStack stack) {
        ItemStack left = insert(inventory, stack);
        if (!left.isEmpty()) {
            var returned =
                    new ItemEntity(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, left);
            returned.setDefaultPickUpDelay();
            level.addFreshEntity(returned);
        }
    }
}
