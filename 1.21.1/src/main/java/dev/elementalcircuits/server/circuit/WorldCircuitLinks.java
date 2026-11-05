package dev.elementalcircuits.server.circuit;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.persistence.JsonNbt;
import dev.elementalcircuits.adapter.resources.AssemblyResources;
import dev.elementalcircuits.application.runtime.CircuitExchange;
import dev.elementalcircuits.block.CircuitBlockEntity;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.CircuitAssembly;
import dev.elementalcircuits.domain.circuit.CircuitLinks;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/** Cross-block links are anchored, bounded and persisted independently of chunk load order. */
public final class WorldCircuitLinks extends SavedData implements CircuitExchange {
    private CircuitLinks links = new CircuitLinks(AssemblyResources.rules());

    private record Anchor(BlockPos pos, UUID owner, boolean removed) {}

    private final Map<UUID, Anchor> anchors = new HashMap<>();
    private CompoundTag invalid;

    public static WorldCircuitLinks get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(
                        new SavedData.Factory<>(
                                WorldCircuitLinks::new, (t, r) -> WorldCircuitLinks.load(t)),
                        "elementalcircuits_links");
    }

    public CircuitLinks graph() {
        if (invalid != null)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.circuit._world_circuit_links.001"));
        return links;
    }

    public boolean references(UUID carrier, int slot) {
        return invalid != null || links.references(carrier, slot);
    }

    public boolean anchored(UUID id, BlockPos pos) {
        return !anchors.containsKey(id)
                || !anchors.get(id).removed && anchors.get(id).pos.equals(pos);
    }

    public void connect(
            ServerLevel level, ServerPlayer player, UUID editing, JsonCodecs.LinkRequest request) {
        var from = WorldCircuits.block(level, request.from().carrier());
        var to = WorldCircuits.block(level, request.to().carrier());
        if (from == null
                || to == null
                || from == to
                || !from.owns(player)
                || !to.owns(player)
                || !request.from().carrier().equals(editing)
                        && !request.to().carrier().equals(editing))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.circuit._world_circuit_links.002"));
        int range = Math.min(from.assembly().rules().range(), to.assembly().rules().range());
        if (from.getBlockPos().distSqr(to.getBlockPos()) > range * range)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.circuit._world_circuit_links.003"));
        if (!anchored(request.from().carrier(), from.getBlockPos())
                || !anchored(request.to().carrier(), to.getBlockPos()))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.circuit._world_circuit_links.004"));
        if (from.assembly().links().connections().stream()
                .anyMatch(c -> c.from().equals(request.from())))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.circuit._world_circuit_links.005"));
        graph().connect(
                        request.from(),
                        request.to(),
                        request.payload(),
                        id ->
                                id.equals(from.assembly().identity())
                                        ? from.assembly()
                                        : id.equals(to.assembly().identity())
                                                ? to.assembly()
                                                : null);
        anchors.put(
                request.from().carrier(),
                new Anchor(from.getBlockPos().immutable(), from.owner(), false));
        anchors.put(
                request.to().carrier(),
                new Anchor(to.getBlockPos().immutable(), to.owner(), false));
        setDirty();
    }

    /** Called only on actual block replacement, never on chunk unload or setRemoved(). */
    public void removed(UUID id, BlockPos position, UUID owner) {
        Anchor a = anchors.get(id);
        if (invalid != null || a == null || !a.pos.equals(position)) return;
        if (a.owner != null && !a.owner.equals(owner)) return;
        anchors.put(id, new Anchor(a.pos, owner, true));
        setDirty();
    }

    private boolean destroyed(ServerLevel level, UUID id) {
        Anchor a = anchors.get(id);
        if (a == null) return false;
        if (a.removed) return true;
        if (!level.hasChunkAt(a.pos)) return false;
        if (level.getBlockEntity(a.pos) instanceof CircuitBlockEntity block)
            try {
                if (block.assembly().identity().equals(id)) return false;
            } catch (RuntimeException error) {
                return false;
            }
        // A loaded position without its identity proves removal; an unloaded position proves
        // nothing.
        anchors.put(id, new Anchor(a.pos, a.owner, true));
        setDirty();
        return true;
    }

    public void disconnect(ServerLevel level, ServerPlayer player, UUID editing, int id) {
        var c =
                graph().connections().stream()
                        .filter(l -> l.id() == id)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                dev.elementalcircuits.text.Texts.get(
                                                        "text.elementalcircuits.server.circuit._world_circuit_links.006")));
        if (!c.from().carrier().equals(editing) && !c.to().carrier().equals(editing))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.circuit._world_circuit_links.007"));
        var from = WorldCircuits.block(level, c.from().carrier());
        var to = WorldCircuits.block(level, c.to().carrier());
        // A recovered item placed elsewhere is not the original transport endpoint.
        if (from != null && !anchored(c.from().carrier(), from.getBlockPos())) from = null;
        if (to != null && !anchored(c.to().carrier(), to.getBlockPos())) to = null;
        if (from != null && to != null) {
            if (!from.owns(player) || !to.owns(player))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.circuit._world_circuit_links.008"));
            var source = from.assembly();
            var target = to.assembly();
            graph().disconnect(
                            id,
                            k ->
                                    k.equals(c.from().carrier())
                                            ? source
                                            : k.equals(c.to().carrier()) ? target : null);
        } else {
            var survivor = from != null ? from : to;
            UUID missing = from == null ? c.from().carrier() : c.to().carrier();
            Anchor lost = anchors.get(missing);
            if (survivor == null
                    || !survivor.owns(player)
                    || !survivor.assembly().identity().equals(editing)
                    || lost == null
                    || lost.owner == null
                    || !lost.owner.equals(player.getUUID())
                    || !destroyed(level, missing))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.circuit._world_circuit_links.009"));
            if (survivor.assembly().powered())
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.circuit._world_circuit_links.010"));
            graph().abandon(id); // In-flight mana enters the loss ledger; never credited to the
            // survivor.
        }
        anchors.keySet().removeIf(k -> !links.references(k, 0));
        setDirty();
    }

    public void deliver(java.util.function.Function<UUID, CircuitAssembly> resolver) {
        if (invalid == null) {
            var before = links.changeToken();
            links.deliver(resolver);
            if (before != links.changeToken()) setDirty();
        }
    }

    public void publish(java.util.function.Function<UUID, CircuitAssembly> resolver) {
        if (invalid == null) {
            var before = links.changeToken();
            links.publish(resolver);
            if (before != links.changeToken()) setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        if (invalid != null) return invalid.copy();
        tag.putInt("Version", 2);
        tag.put("Links", JsonNbt.encode(JsonCodecs.links(links), 1048576));
        ListTag list = new ListTag();
        anchors.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        e -> {
                            CompoundTag t = new CompoundTag();
                            t.putUUID("Identity", e.getKey());
                            t.putLong("Pos", e.getValue().pos.asLong());
                            if (e.getValue().owner != null) t.putUUID("Owner", e.getValue().owner);
                            t.putBoolean("Removed", e.getValue().removed);
                            list.add(t);
                        });
        tag.put("Anchors", list);
        return tag;
    }

    public static WorldCircuitLinks load(CompoundTag tag) {
        WorldCircuitLinks data = new WorldCircuitLinks();
        try {
            if (!tag.contains("Version", Tag.TAG_INT)
                    || tag.getInt("Version") < 1
                    || tag.getInt("Version") > 2
                    || !tag.contains("Links")
                    || !tag.contains("Anchors", Tag.TAG_LIST))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.circuit._world_circuit_links.011"));
            data.links = JsonCodecs.readLinks(JsonNbt.decode(tag.get("Links"), 1048576));
            ListTag list = tag.getList("Anchors", Tag.TAG_COMPOUND);
            if (list.size() > 64 || ((ListTag) tag.get("Anchors")).size() != list.size())
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.circuit._world_circuit_links.012"));
            for (int i = 0; i < list.size(); i++) {
                var t = list.getCompound(i);
                if (!t.hasUUID("Identity")
                        || !t.contains("Pos", Tag.TAG_LONG)
                        || t.contains("Owner") && !t.hasUUID("Owner")
                        || tag.getInt("Version") == 2
                                && (!t.contains("Removed", Tag.TAG_BYTE)
                                        || t.getByte("Removed") < 0
                                        || t.getByte("Removed") > 1))
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.circuit._world_circuit_links.013"));
                var anchor =
                        new Anchor(
                                BlockPos.of(t.getLong("Pos")),
                                t.hasUUID("Owner") ? t.getUUID("Owner") : null,
                                t.getBoolean("Removed"));
                if (data.anchors.put(t.getUUID("Identity"), anchor) != null)
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.circuit._world_circuit_links.014"));
            }
            for (var c : data.links.connections())
                if (c.from().carrier().equals(c.to().carrier())
                        || !data.anchors.containsKey(c.from().carrier())
                        || !data.anchors.containsKey(c.to().carrier()))
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.circuit._world_circuit_links.015"));
            if (tag.getInt("Version") == 1) data.setDirty();
        } catch (RuntimeException e) {
            data.invalid = tag.copy();
            org.slf4j.LoggerFactory.getLogger("ElementalCircuits")
                    .error(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.circuit._world_circuit_links.016"),
                            e.getMessage());
        }
        return data;
    }
}
