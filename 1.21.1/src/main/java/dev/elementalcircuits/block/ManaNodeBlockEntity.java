package dev.elementalcircuits.block;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.adapter.resources.ManaFieldResources;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.mana.ManaFieldRules;
import dev.elementalcircuits.domain.mana.ManaNode;
import dev.elementalcircuits.server.mana.CrystalAmplification;
import dev.elementalcircuits.server.mana.WorldManaField;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ManaNodeBlockEntity extends BlockEntity {
    private ManaNode node;
    private ManaFieldRules rules;
    private CompoundTag invalid;

    public ManaNodeBlockEntity(BlockPos pos, BlockState state) {
        super(ElementalCircuits.MANA_NODE_ENTITY.get(), pos, state);
    }

    public boolean natural() {
        return getBlockState().is(ElementalCircuits.NATURAL_MANA_NODE.get()) || aerial();
    }

    public boolean aerial() {
        return getBlockState().is(ElementalCircuits.AERIAL_MANA_NODE.get());
    }

    private void initialize() {
        if (invalid != null)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.block._mana_node_block_entity.001"));
        if (node == null) {
            rules =
                    level instanceof ServerLevel server && WorldManaField.get(server).valid()
                            ? WorldManaField.get(server).field().rules()
                            : ManaFieldResources.defaults();
            if (aerial())
                rules =
                        new ManaFieldRules(
                                rules.version(),
                                rules.cellSize(),
                                rules.stepTicks(),
                                rules.capacity(),
                                rules.ambient(),
                                rules.diffusionDivisor(),
                                rules.decayDivisor(),
                                rules.intakePerTick(),
                                rules.crystal(),
                                dev.elementalcircuits.adapter.config.ServerSettings.mana(
                                        "field.aerialCapacity"),
                                rules.artificialCapacity(),
                                Math.min(
                                        rules.capacity(),
                                        dev.elementalcircuits.adapter.config.ServerSettings.mana(
                                                "field.aerialPerStep")),
                                rules.artificialPerStep());
            node =
                    new ManaNode(
                            rules, natural() ? ManaNode.Kind.NATURAL : ManaNode.Kind.ARTIFICIAL);
            setChanged();
        }
    }

    public boolean usable() {
        return invalid == null;
    }

    private long baseStepLimit() {
        return level instanceof ServerLevel server
                ? (natural()
                        ? (aerial()
                                ? rules.naturalPerStep()
                                : WorldManaField.get(server).field().rules().naturalPerStep())
                        : WorldManaField.get(server).field().rules().artificialPerStep())
                : (natural() ? rules.naturalPerStep() : rules.artificialPerStep());
    }

    private long stepLimit() {
        return CrystalAmplification.scale(
                level, worldPosition, baseStepLimit(), "crystal.nodeEmissionMultiplier");
    }

    public long emission() {
        initialize();
        return Math.min(node.remaining(), stepLimit());
    }

    public void emitted(long amount) {
        initialize();
        node.emitted(amount, stepLimit());
        setChanged();
        appearance();
    }

    public void regenerate(long perSecond, int ticks) {
        initialize();
        var before = node.snapshot();
        node.regenerate(
                CrystalAmplification.scale(
                        level, worldPosition, perSecond, "crystal.naturalRecoveryMultiplier"),
                ticks);
        if (!before.equals(node.snapshot())) {
            setChanged();
            appearance();
        }
    }

    public long remaining() {
        initialize();
        return node.remaining();
    }

    public long capacity() {
        initialize();
        return node.capacity();
    }

    public boolean insert() {
        initialize();
        if (!node.insert()) return false;
        setChanged();
        appearance();
        return true;
    }

    private void appearance() {
        if (level != null
                && !level.isClientSide
                && getBlockState().getValue(ManaNodeBlock.LIT) != (node.remaining() > 0))
            level.setBlock(
                    worldPosition,
                    getBlockState().setValue(ManaNodeBlock.LIT, node.remaining() > 0),
                    3);
    }

    public void placed(ItemStack stack) {
        if (natural()) return;
        var root = ItemData.read(stack);
        if (root.contains("ManaNode")) {
            try {
                if (!root.contains("ManaNode", Tag.TAG_COMPOUND))
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.block._mana_node_block_entity.002"));
                restore(root.getCompound("ManaNode"));
            } catch (RuntimeException error) {
                invalid = new CompoundTag();
                invalid.put("Node", root.get("ManaNode").copy());
                node = null;
            }
        }
        if (invalid == null) {
            initialize();
            appearance();
        }
        setChanged();
    }

    public ItemStack drop() {
        if (natural()) {
            if (invalid != null) return ItemStack.EMPTY;
            initialize();
            int count = (int) Math.min(2, node.remaining() / rules.crystal());
            return count == 0
                    ? ItemStack.EMPTY
                    : new ItemStack(ElementalCircuits.MANA_CRYSTAL.get(), count);
        }
        ItemStack stack = new ItemStack(ElementalCircuits.MANA_NODE_ITEM.get());
        if (invalid != null)
            ItemData.put(
                    stack,
                    "ManaNode",
                    invalid.get("Node") == null ? invalid.copy() : invalid.get("Node").copy());
        else {
            initialize();
            ItemData.put(stack, "ManaNode", ManaFieldPersistence.node(node, rules));
        }
        return stack;
    }

    private void restore(CompoundTag tag) {
        var candidate = ManaFieldPersistence.node(tag);
        if ((candidate.kind() == ManaNode.Kind.NATURAL) != natural())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.block._mana_node_block_entity.003"));
        rules = ManaFieldPersistence.rules(tag);
        node = candidate;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) WorldManaField.get(server).register(this);
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel server) WorldManaField.get(server).unregister(this);
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(
            CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (invalid != null) {
            tag.merge(invalid);
            return;
        }
        initialize();
        tag.put("Node", ManaFieldPersistence.node(node, rules));
    }

    @Override
    protected void loadAdditional(
            CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        node = null;
        invalid = null;
        try {
            if (!tag.contains("Node", Tag.TAG_COMPOUND))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.block._mana_node_block_entity.004"));
            restore(tag.getCompound("Node"));
        } catch (RuntimeException error) {
            invalid = tag.copy();
        }
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return new CompoundTag();
    }

    @Override
    public void handleUpdateTag(
            CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {}
}
