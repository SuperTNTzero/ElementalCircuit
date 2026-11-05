package dev.elementalcircuits.adapter.persistence;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.resources.AssemblyResources;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.CircuitAssembly;
import dev.elementalcircuits.domain.circuit.TickCircuit;

import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Server-thread repository. Each physical stack has one live aggregate and one committed encoding.
 */
public final class CarrierRepository {
    private static final String KEY = "ElementalCircuit";
    private static final int LIMIT = 67108864;
    private static final Map<ItemStack, Entry> CACHE = new WeakHashMap<>();

    private static final class Entry {
        final CircuitAssembly assembly;
        CircuitAssembly.ChangeStamp saved;
        Tag encoded;
        net.minecraft.world.item.component.CustomData component;

        Entry(CircuitAssembly assembly, Tag encoded) {
            this.assembly = assembly;
            this.encoded = encoded;
            saved = assembly.changeStamp();
        }
    }

    private CarrierRepository() {}

    private static CompoundTag container(ItemStack stack) {
        CompoundTag root = ItemData.read(stack);
        if (root != null && root.contains(KEY) && !root.contains(KEY, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.persistence._carrier_repository.001"));
        }
        return root.getCompound(KEY);
    }

    public static UUID identity(ItemStack stack) {
        Entry cached = CACHE.get(stack);
        if (cached != null
                && cached.component
                        == stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA))
            return cached.assembly.identity();
        CompoundTag tag = container(stack);
        if (tag.contains("Identity") && !tag.hasUUID("Identity")) {
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.persistence._carrier_repository.002"));
        }
        if (!tag.hasUUID("Identity")) {
            if (tag.contains("Assembly"))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.persistence._carrier_repository.003"));
            if (tag.contains("Runtime")) {
                JsonCodecs.readSnapshot(
                        JsonNbt.decode(tag.get("Runtime"), JsonCodecs.SNAPSHOT_LIMIT));
            }
            tag.putUUID("Identity", UUID.randomUUID());
            ItemData.put(stack, KEY, tag);
        }
        return tag.getUUID("Identity");
    }

    public static CircuitAssembly load(ItemStack stack) {
        Entry existing = CACHE.get(stack);
        if (existing != null
                && existing.component
                        == stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA))
            return existing.assembly;
        UUID identity = identity(stack);
        CompoundTag tag = container(stack);
        Entry cached = CACHE.get(stack);
        // Replacement NBT invalidates the cache. The repository exclusively owns live encoding
        // writes.
        if (cached != null
                && Objects.equals(cached.encoded, tag.get("Assembly"))
                && identity.equals(cached.assembly.identity())) {
            cached.component = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            return cached.assembly;
        }
        CircuitAssembly assembly;
        if (tag.contains("Assembly")) {
            assembly = JsonCodecs.readAssembly(JsonNbt.decode(tag.get("Assembly"), LIMIT));
            if (!identity.equals(assembly.identity()))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.persistence._carrier_repository.004"));
        } else {
            TickCircuit first =
                    tag.contains("Runtime")
                            ? JsonCodecs.readSnapshot(
                                    JsonNbt.decode(tag.get("Runtime"), JsonCodecs.SNAPSHOT_LIMIT))
                            : new TickCircuit(Blueprint.empty(ServerSettings.circuitRules()));
            assembly = new CircuitAssembly(identity, AssemblyResources.rules(), first);
        }
        var legacyStamp = assembly.changeStamp();
        assembly.prepareBoards(ServerSettings.boardSafety());
        Tag encoded = tag.get("Assembly");
        if (!(encoded instanceof CompoundTag) || !legacyStamp.equals(assembly.changeStamp())) {
            // Validate and encode completely before replacing a legacy record.
            encoded = JsonNbt.encode(JsonCodecs.assembly(assembly), LIMIT);
            tag.put("Assembly", encoded);
            tag.remove("Runtime");
            ItemData.put(stack, KEY, tag);
        }
        var entry = new Entry(assembly, encoded);
        entry.component = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        CACHE.put(stack, entry);
        return assembly;
    }

    /**
     * True means persisted bytes were replaced; idle callers perform no full snapshot or JSON
     * write.
     */
    public static boolean commit(ItemStack stack) {
        CircuitAssembly assembly = load(stack);
        var policy = ServerSettings.boardSafety();
        assembly.prepareBoards(policy);
        Entry entry = CACHE.get(stack);
        CircuitAssembly.ChangeStamp stamp = assembly.changeStamp();
        var oldAppearance = BoardAppearance.read(stack);
        long capacity =
                policy.profiles()
                        .get(assembly.circuit(assembly.selected()).definition().rules().material())
                        .capacity();
        if (stamp.equals(entry.saved)
                && oldAppearance != null
                && oldAppearance.capacity() == capacity) return false;
        Tag encoded = JsonNbt.encode(JsonCodecs.assembly(assembly), LIMIT);
        var updated = container(stack);
        updated.put("Assembly", encoded);
        var condition = assembly.board(assembly.selected());
        var appearance = new CompoundTag();
        appearance.putInt("Slot", assembly.selected());
        appearance.putLong("Remaining", condition.remaining());
        appearance.putLong("Maximum", condition.maximum());
        appearance.putLong("Capacity", capacity);
        appearance.putBoolean("Melted", condition.melted());
        ItemData.update(
                stack,
                root -> {
                    root.put(KEY, updated);
                    root.put("BoardStatus", appearance);
                });
        entry.saved = stamp;
        entry.encoded = encoded;
        entry.component = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return true;
    }
}
