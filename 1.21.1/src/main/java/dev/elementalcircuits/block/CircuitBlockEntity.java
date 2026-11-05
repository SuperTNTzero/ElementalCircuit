package dev.elementalcircuits.block;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.persistence.CarrierRepository;
import dev.elementalcircuits.adapter.persistence.ItemData;
import dev.elementalcircuits.adapter.persistence.NativeStackCodec;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.CircuitAssembly;
import dev.elementalcircuits.domain.mana.ManaSource;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.circuit.FixedCircuitAppearance;
import dev.elementalcircuits.server.circuit.WorldCircuits;
import dev.elementalcircuits.server.mana.PlayerMana;
import dev.elementalcircuits.server.mana.RefundReceipts;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Fixed carrier owns its entire assembly and finite charge. No automatic or offline refill. */
public final class CircuitBlockEntity extends BlockEntity implements ManaSource {
    private ItemStack carrier;
    private CompoundTag invalid;

    public CircuitBlockEntity(BlockPos pos, BlockState state) {
        super(ElementalCircuits.CIRCUIT_ENTITY.get(), pos, state);
        carrier = new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get());
    }

    public ItemStack carrier() {
        if (invalid != null)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.block._circuit_block_entity.001"));
        return carrier;
    }

    public CircuitAssembly assembly() {
        return CarrierRepository.load(carrier());
    }

    public void refreshMaterial() {
        if (level != null && !level.isClientSide) {
            var material = BoardMaterial.of(assembly().circuit(1).definition().rules().material());
            if (getBlockState().getValue(CircuitBlock.MATERIAL) != material)
                level.setBlock(
                        worldPosition,
                        getBlockState().setValue(CircuitBlock.MATERIAL, material),
                        3);
        }
    }

    public UUID owner() {
        var t = store();
        return t.hasUUID("Owner") ? t.getUUID("Owner") : new UUID(0, 0);
    }

    public void placed(ItemStack stack, Player player) {
        if (invalid != null)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.block._circuit_block_entity.002"));
        var candidate = stack.copy();
        candidate.setCount(1);
        candidate.remove(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        available(candidate);
        var data = fixedStore(candidate);
        data.putUUID("Owner", player.getUUID());
        ItemData.put(candidate, "FixedCircuit", data);
        carrier = candidate;
        FixedCircuitAppearance.update(carrier);
        refreshMaterial();
        setChanged();
    }

    public boolean owns(ServerPlayer player) {
        return player.getUUID().equals(owner())
                && player.getAbilities().mayBuild
                && player.serverLevel() == level
                && level.mayInteract(player, worldPosition);
    }

    public boolean allowed(ServerPlayer player) {
        return owns(player) && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 64;
    }

    private static CompoundTag fixedStore(ItemStack stack) {
        var t = ItemData.section(stack, "FixedCircuit");
        if (t.contains("Owner") && !t.hasUUID("Owner"))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.block._circuit_block_entity.003"));
        return t;
    }

    private CompoundTag store() {
        return fixedStore(carrier());
    }

    private static long counter(CompoundTag t, String key) {
        if (t.contains(key) && !t.contains(key, Tag.TAG_LONG))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.block._circuit_block_entity.004"));
        long value = t.getLong(key);
        if (value < 0)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.block._circuit_block_entity.005"));
        return value;
    }

    private static long available(ItemStack stack) {
        var t = fixedStore(stack);
        long value = counter(t, "Charge"),
                spent = counter(t, "Spent"),
                received = counter(t, "Received");
        if (value > CarrierRepository.load(stack).rules().blockCapacity()
                || value > received
                || received - value != spent)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.block._circuit_block_entity.006"));
        return value;
    }

    @Override
    public long available() {
        return available(carrier());
    }

    @Override
    public boolean debit(long amount) {
        Fixed.checked(amount);
        long charge = available(), spent = store().getLong("Spent");
        if (amount == 0) return true;
        if (amount > charge || spent < 0 || spent > Long.MAX_VALUE - amount) return false;
        var data = store();
        data.putLong("Charge", charge - amount);
        data.putLong("Spent", spent + amount);
        ItemData.put(carrier, "FixedCircuit", data);
        setChanged();
        return true;
    }

    public long refund(String receipt, long total) {
        var data = ItemData.read(carrier());
        long old = data.getCompound(RefundReceipts.KEY).getLong(receipt);
        if (old == total) return old;
        long credited = RefundReceipts.credit(data, receipt, total, this::refund);
        if (credited > old) {
            ItemData.put(carrier, RefundReceipts.KEY, data.getCompound(RefundReceipts.KEY));
            setChanged();
        }
        return credited;
    }

    public long refund(long offered) {
        Fixed.checked(offered);
        long charge = available(),
                spent = store().getLong("Spent"),
                accepted =
                        Math.min(
                                offered,
                                Math.min(spent, assembly().rules().blockCapacity() - charge));
        if (accepted == 0) return 0;
        var data = store();
        data.putLong("Charge", charge + accepted);
        data.putLong("Spent", spent - accepted);
        ItemData.put(carrier, "FixedCircuit", data);
        setChanged();
        return accepted;
    }

    public long charge(ServerPlayer player) {
        if (!allowed(player))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.block._circuit_block_entity.007"));
        long
                amount =
                        Math.min(
                                assembly().rules().chargeStep(),
                                assembly().rules().blockCapacity() - available()),
                received = store().getLong("Received");
        if (amount == 0)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.block._circuit_block_entity.008"));
        if (received < 0 || received > Long.MAX_VALUE - amount)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.block._circuit_block_entity.009"));
        if (!PlayerMana.get(player).debit(amount))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.block._circuit_block_entity.010")
                            + Fixed.display(amount));
        var data = store();
        data.putLong("Charge", available() + amount);
        data.putLong("Received", received + amount);
        ItemData.put(carrier, "FixedCircuit", data);
        PlayerMana.save(player);
        setChanged();
        return amount;
    }

    public Vec3 direction() {
        return Vec3.atLowerCornerOf(getBlockState().getValue(CircuitBlock.FACING).getNormal());
    }

    public Vec3 origin() {
        return Vec3.atCenterOf(worldPosition).add(direction().scale(.65)).add(0, .1, 0);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && invalid == null)
            try {
                refreshMaterial();
            } catch (RuntimeException ignored) {
                /* Keep damaged source data for the existing fault boundary. */
            }
        WorldCircuits.register(this);
    }

    @Override
    public void setRemoved() {
        WorldCircuits.unregister(this);
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(
            CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (invalid != null) tag.merge(invalid);
        else {
            CarrierRepository.commit(carrier);
            tag.put("Carrier", carrier.save(registries));
        }
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return new CompoundTag();
    }

    @Override
    public void handleUpdateTag(
            CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        /* Client needs only the block facing; assembly remains server-owned. */
    }

    @Override
    protected void loadAdditional(
            CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        invalid = null;
        try {
            if (!tag.contains("Carrier", Tag.TAG_COMPOUND))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.block._circuit_block_entity.011"));
            ItemStack loaded = NativeStackCodec.read(registries, tag.getCompound("Carrier"));
            if (!loaded.is(ElementalCircuits.CIRCUIT_ITEM.get()) || loaded.getCount() != 1)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.block._circuit_block_entity.012"));
            carrier = loaded;
            owner();
            available();
        } catch (RuntimeException e) {
            invalid = tag.copy();
        }
    }
}
