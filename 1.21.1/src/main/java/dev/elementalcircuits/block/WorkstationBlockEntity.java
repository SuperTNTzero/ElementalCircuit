package dev.elementalcircuits.block;

import dev.elementalcircuits.*;
import dev.elementalcircuits.adapter.resources.WandResources;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.equipment.WandParts;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.circuit.CarrierService;
import dev.elementalcircuits.server.equipment.WandEquipment;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** Transient visual projection of an editing target, never an inventory or a second wand. */
public final class WorkstationBlockEntity extends BlockEntity {
    private UUID viewer;
    private ItemStack target = ItemStack.EMPTY, held = ItemStack.EMPTY;
    private WandParts.Assembly parts;

    public WorkstationBlockEntity(BlockPos pos, BlockState state) {
        super(ElementalCircuits.WORKSTATION_ENTITY.get(), pos, state);
    }

    public WandParts.Assembly parts() {
        return parts;
    }

    public void show(ServerPlayer p, ItemStack stack) {
        show(p, stack, stack);
    }

    public void show(ServerPlayer p, ItemStack stack, ItemStack held) {
        viewer = p.getUUID();
        target = stack;
        this.held = held;
        parts =
                getBlockState().is(ElementalCircuits.WAND_WORKBENCH.get())
                                && stack.is(ElementalCircuits.WAND.get())
                        ? WandEquipment.assembly(stack)
                        : null;
        if (getBlockState().hasProperty(InscriptionTable.BOARD))
            level.setBlock(
                    worldPosition,
                    getBlockState()
                            .setValue(
                                    InscriptionTable.BOARD,
                                    BoardMaterial.of(
                                            CarrierService.assembly(stack)
                                                    .circuit(
                                                            CarrierService.assembly(stack)
                                                                    .selected())
                                                    .definition()
                                                    .rules()
                                                    .material())),
                    3);
        changed();
    }

    public void clear() {
        viewer = null;
        target = ItemStack.EMPTY;
        held = ItemStack.EMPTY;
        parts = null;
        if (level != null
                && getBlockState().hasProperty(InscriptionTable.BOARD)
                && getBlockState().getValue(InscriptionTable.BOARD) != BoardMaterial.EMPTY)
            level.setBlock(
                    worldPosition,
                    getBlockState().setValue(InscriptionTable.BOARD, BoardMaterial.EMPTY),
                    3);
        changed();
    }

    public void clear(UUID player) {
        if (player.equals(viewer)) clear();
    }

    private void changed() {
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public static void tick(
            net.minecraft.world.level.Level level,
            BlockPos pos,
            BlockState state,
            WorkstationBlockEntity b) {
        if (level.getGameTime() % 10 != 0) return;
        try {
            var p = b.viewer == null ? null : ((ServerLevel) level).getPlayerByUUID(b.viewer);
            if (p == null
                    || !p.isAlive()
                    || p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 64
                    || b.target.isEmpty()
                    || p.getMainHandItem() != b.held && p.getOffhandItem() != b.held) {
                if (b.viewer != null
                        || b.parts != null
                        || state.hasProperty(InscriptionTable.BOARD)
                                && state.getValue(InscriptionTable.BOARD) != BoardMaterial.EMPTY)
                    b.clear();
                return;
            }
            if (state.hasProperty(InscriptionTable.BOARD)) {
                var a = CarrierService.assembly(b.target);
                var material =
                        BoardMaterial.of(a.circuit(a.selected()).definition().rules().material());
                if (state.getValue(InscriptionTable.BOARD) != material)
                    level.setBlock(pos, state.setValue(InscriptionTable.BOARD, material), 3);
            }
            if (b.parts != null) {
                var next = WandEquipment.assembly(b.target);
                if (!next.equals(b.parts)) {
                    b.parts = next;
                    b.changed();
                }
            }
        } catch (RuntimeException invalidPreview) {
            b.clear();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) clear();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var t = new CompoundTag();
        if (parts != null) {
            t.putString("Head", parts.head());
            t.putString("Body", parts.body());
            t.putString("Tail", parts.tail());
        }
        return t;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(
            net.minecraft.network.Connection connection,
            ClientboundBlockEntityDataPacket packet,
            HolderLookup.Provider registries) {
        handleUpdateTag(packet.getTag(), registries);
    }

    @Override
    public void handleUpdateTag(CompoundTag t, HolderLookup.Provider registries) {
        parts = null;
        try {
            if (t.contains("Head", Tag.TAG_STRING)) {
                var next =
                        new WandParts.Assembly(
                                t.getString("Head"), t.getString("Body"), t.getString("Tail"));
                next.stats(WandResources.definitions());
                parts = next;
            }
        } catch (RuntimeException ignored) {
        }
    }
}
