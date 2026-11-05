package dev.elementalcircuits.server.mana;

import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.ManaFieldPersistence;
import dev.elementalcircuits.block.ManaNodeBlockEntity;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.CircuitBounds;
import dev.elementalcircuits.domain.mana.ManaField;
import dev.elementalcircuits.domain.mana.ManaOrigin;
import dev.elementalcircuits.domain.mana.ManaSource;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** Per-dimension finite field. No chunk loading, wall-clock catch-up or client mutation. */
public final class WorldManaField extends SavedData {
    private ManaField field;
    private CompoundTag invalid;
    private CompoundTag receipts = new CompoundTag();
    private final NavigableSet<ManaField.Cell> active = new TreeSet<>();
    private final Map<Long, List<ManaField.Cell>> byChunk = new HashMap<>();
    private final NavigableMap<BlockPos, ManaNodeBlockEntity> nodes = new TreeMap<>();
    private long preparedTick = Long.MIN_VALUE;

    public WorldManaField() {
        field = new ManaField(ServerSettings.fieldRules());
    }

    public static WorldManaField get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(
                        new SavedData.Factory<>(WorldManaField::new, (t, r) -> load(t)),
                        "elementalcircuits_mana_field");
    }

    public ManaField field() {
        if (invalid != null)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.mana._world_mana_field.001"));
        return field;
    }

    public boolean valid() {
        return invalid == null;
    }

    private static long chunk(ManaField.Cell c) {
        return net.minecraft.world.level.ChunkPos.asLong(
                Math.floorDiv(c.x(), 2), Math.floorDiv(c.z(), 2));
    }

    private void index(ManaField.Cell c) {
        byChunk.computeIfAbsent(chunk(c), k -> new ArrayList<>()).add(c);
    }

    public void loaded(int x, int z) {
        if (invalid == null)
            active.addAll(
                    byChunk.getOrDefault(
                            net.minecraft.world.level.ChunkPos.asLong(x, z), List.of()));
    }

    public void unloaded(int x, int z) {
        active.removeAll(
                byChunk.getOrDefault(net.minecraft.world.level.ChunkPos.asLong(x, z), List.of()));
    }

    private static boolean loaded(ServerLevel level, ManaField.Cell c) {
        return c.y() * 8 >= level.getMinBuildHeight()
                && c.y() * 8 < level.getMaxBuildHeight()
                && level.hasChunk(Math.floorDiv(c.x(), 2), Math.floorDiv(c.z(), 2));
    }

    public ManaField.Cell discover(ServerLevel level, BlockPos pos) {
        ManaField.Cell center = ManaField.Cell.at(pos.getX(), pos.getY(), pos.getZ());
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs((long) center.x() + dx) > 3750000
                            || Math.abs((long) center.z() + dz) > 3750000
                            || center.y() + dy < -512
                            || center.y() + dy > 511) continue;
                    ManaField.Cell c = center.offset(dx, dy, dz);
                    if (!loaded(level, c)) continue;
                    if (field().cells().size() >= ServerSettings.count("field.storedCellBudget")
                            && !field().cells().contains(c)) continue;
                    if (field().discover(c)) {
                        index(c);
                        setDirty();
                    }
                    active.add(c);
                }
        return center;
    }

    /** Observation only; no discovery, generation or loading. */
    public boolean naturalNodeNear(BlockPos pos, int radius) {
        return invalid == null
                && nodes.values().stream()
                        .anyMatch(
                                node ->
                                        !node.isRemoved()
                                                && node.usable()
                                                && node.natural()
                                                && node.getBlockPos().distSqr(pos)
                                                        <= (long) radius * radius);
    }

    public void register(ManaNodeBlockEntity node) {
        if (invalid == null) nodes.put(node.getBlockPos().immutable(), node);
    }

    public void unregister(ManaNodeBlockEntity node) {
        nodes.remove(node.getBlockPos(), node);
    }

    public ManaSource tap(ServerLevel level, BlockPos pos) {
        return tap(level, pos, 1);
    }

    public ManaSource tap(ServerLevel level, BlockPos pos, int mouths) {
        if (invalid != null || ServerSettings.count("field.enabled") == 0) return ManaSource.NONE;
        ManaField.Cell cell = discover(level, pos);
        if (mouths < 1 || mouths > CircuitBounds.RUNES * 4)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.mana._world_mana_field.002"));
        long base = Math.min(Fixed.MAX, field.rules().intakePerTick() * mouths),
                reference = ServerSettings.mana("field.intakeReference");
        long quota =
                java.math.BigInteger.valueOf(base)
                        .multiply(
                                java.math.BigInteger.valueOf(
                                        Math.min(reference, field.amount(cell))))
                        .divide(java.math.BigInteger.valueOf(reference))
                        .longValueExact();
        return new ManaSource() {
            long remaining = quota;

            public ManaOrigin provenance() {
                return new ManaOrigin(
                        "field/"
                                + level.dimension().location()
                                + "/"
                                + cell.x()
                                + ","
                                + cell.y()
                                + ","
                                + cell.z());
            }

            public long available() {
                return Math.min(remaining, field.amount(cell));
            }

            public boolean debit(long amount) {
                Fixed.checked(amount);
                if (amount > remaining || !loaded(level, cell)) return false;
                if (!field.debit(cell, amount)) return false;
                remaining -= amount;
                if (amount > 0) setDirty();
                return true;
            }
        };
    }

    public void tick(ServerLevel level) {
        if (invalid != null
                || ServerSettings.count("field.enabled") == 0
                || preparedTick == level.getGameTime()) return;
        preparedTick = level.getGameTime();
        nodes.entrySet().removeIf(e -> e.getValue().isRemoved() || !level.hasChunkAt(e.getKey()));
        // Node activation precedes diffusion. Sinks run later in WorldCircuits' stable host order.
        if (field.phase() == 0 || active.isEmpty())
            for (var node : List.copyOf(nodes.values()))
                if (node.usable()) discover(level, node.getBlockPos());
        // Between diffusion steps only advance the phase: do not copy or scan the entire active
        // field at 20 Hz.
        if (!active.isEmpty() && field.phase() + 1 < field.rules().stepTicks()) {
            field.tick(List.of(active.first()));
            setDirty();
            return;
        }
        active.removeIf(c -> !loaded(level, c));
        int activeBudget = ServerSettings.count("field.activeCellBudget");
        List<ManaField.Cell> all = new ArrayList<>(active), budget = all;
        if (all.size() > activeBudget) {
            // Minecraft persists gameTime; deriving the cursor avoids a hidden, unsaved
            // continuation state.
            int cursor =
                    (int)
                            (Math.floorMod(
                                            level.getGameTime() / field.rules().stepTicks(),
                                            (long) all.size())
                                    * activeBudget
                                    % all.size());
            budget = new ArrayList<>(activeBudget);
            for (int i = 0; i < activeBudget; i++) budget.add(all.get((cursor + i) % all.size()));
        }
        Object before = field.changeToken();
        boolean due = field.tick(budget);
        Set<ManaField.Cell> selected = due ? new HashSet<>(budget) : Set.of();
        if (due)
            for (var node : List.copyOf(nodes.values())) {
                if (!node.usable()) continue;
                ManaField.Cell c =
                        ManaField.Cell.at(
                                node.getBlockPos().getX(),
                                node.getBlockPos().getY(),
                                node.getBlockPos().getZ());
                // Sources outside this step's bounded cell set freeze too, rather than overfilling
                // inactive regions.
                if (!selected.contains(c)) continue;
                node.regenerate(
                        ServerSettings.mana(
                                node.aerial()
                                        ? "field.aerialRegeneration"
                                        : "field.naturalRegeneration"),
                        field.rules().stepTicks());
                long accepted = field.inject(c, node.emission());
                if (accepted > 0) node.emitted(accepted);
            }
        if (field.changeToken() != before) setDirty();
    }

    public long refund(String id, ManaField.Cell cell, long total) {
        long old = receipts.getCompound(RefundReceipts.KEY).getLong(id);
        long credited = RefundReceipts.credit(receipts, id, total, n -> field().inject(cell, n));
        if (credited > old) setDirty();
        return credited;
    }

    public void applySettings() {
        field().reconfigure(ServerSettings.checkedFieldRules());
        setDirty();
    }

    public static WorldManaField load(CompoundTag tag) {
        WorldManaField data = new WorldManaField();
        try {
            data.field = ManaFieldPersistence.read(tag);
            if (tag.contains(RefundReceipts.KEY)
                    && !tag.contains(RefundReceipts.KEY, Tag.TAG_COMPOUND))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.mana._world_mana_field.003"));
            if (tag.contains(RefundReceipts.KEY))
                data.receipts.put(RefundReceipts.KEY, tag.getCompound(RefundReceipts.KEY).copy());
            for (var c : data.field.cells()) data.index(c);
        } catch (RuntimeException error) {
            data.invalid = tag.copy();
            org.slf4j.LoggerFactory.getLogger("ElementalCircuits")
                    .error(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.mana._world_mana_field.004"),
                            error.getMessage());
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        if (invalid != null) {
            tag.merge(invalid);
            return tag;
        }
        tag.merge(ManaFieldPersistence.write(field));
        if (receipts.contains(RefundReceipts.KEY))
            tag.put(RefundReceipts.KEY, receipts.getCompound(RefundReceipts.KEY).copy());
        return tag;
    }
}
