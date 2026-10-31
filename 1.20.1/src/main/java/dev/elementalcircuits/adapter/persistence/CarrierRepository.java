package dev.elementalcircuits.adapter.persistence;

import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.domain.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Server-thread repository. Each physical stack has one live aggregate and one committed encoding. */
public final class CarrierRepository {
    private static final String KEY = "ElementalCircuit";
    private static final int LIMIT = 1048576;
    private static final Map<ItemStack, Entry> CACHE = new WeakHashMap<>();

    private static final class Entry {
        final CircuitAssembly assembly;
        CircuitAssembly.ChangeStamp saved;
        Tag encoded;

        Entry(CircuitAssembly assembly, Tag encoded) {
            this.assembly = assembly;
            this.encoded = encoded;
            saved = assembly.changeStamp();
        }
    }

    private CarrierRepository() {}

    private static CompoundTag container(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root != null && root.contains(KEY) && !root.contains(KEY, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("载体根记录格式损坏");
        }
        return stack.getOrCreateTagElement(KEY);
    }

    public static UUID identity(ItemStack stack) {
        CompoundTag tag = container(stack);
        if (tag.contains("Identity") && !tag.hasUUID("Identity")) {
            throw new IllegalArgumentException("载体身份损坏");
        }
        if (!tag.hasUUID("Identity")) {
            if (tag.contains("Assembly")) throw new IllegalArgumentException("法阵组缺少载体身份");
            if (tag.contains("Runtime")) {
                JsonCodecs.readSnapshot(JsonNbt.decode(tag.get("Runtime"), JsonCodecs.SNAPSHOT_LIMIT));
            }
            tag.putUUID("Identity", UUID.randomUUID());
        }
        return tag.getUUID("Identity");
    }

    public static CircuitAssembly load(ItemStack stack) {
        UUID identity = identity(stack);
        CompoundTag tag = container(stack);
        Entry cached = CACHE.get(stack);
        // Replacement NBT invalidates the cache. The repository exclusively owns live encoding writes.
        if (cached != null && cached.encoded == tag.get("Assembly") && identity.equals(cached.assembly.identity())) {
            return cached.assembly;
        }
        CircuitAssembly assembly;
        if (tag.contains("Assembly")) {
            assembly = JsonCodecs.readAssembly(JsonNbt.decode(tag.get("Assembly"), LIMIT));
            if (!identity.equals(assembly.identity())) throw new IllegalArgumentException("法阵组身份不匹配");
        } else {
            TickCircuit first = tag.contains("Runtime")
                ? JsonCodecs.readSnapshot(JsonNbt.decode(tag.get("Runtime"), JsonCodecs.SNAPSHOT_LIMIT))
                : new TickCircuit(Blueprint.empty(RuleResources.defaults()));
            assembly = new CircuitAssembly(identity, AssemblyResources.rules(), first);
        }
        Tag encoded = tag.get("Assembly");
        if (!(encoded instanceof CompoundTag)) {
            // Validate and encode completely before replacing a legacy record.
            encoded = JsonNbt.encode(JsonCodecs.assembly(assembly), LIMIT);
            tag.put("Assembly", encoded);
            tag.remove("Runtime");
        }
        CACHE.put(stack, new Entry(assembly, encoded));
        return assembly;
    }

    /** True means persisted bytes were replaced; idle callers perform no full snapshot or JSON write. */
    public static boolean commit(ItemStack stack) {
        CircuitAssembly assembly = load(stack);
        Entry entry = CACHE.get(stack);
        CircuitAssembly.ChangeStamp stamp = assembly.changeStamp();
        if (stamp.equals(entry.saved)) return false;
        Tag encoded = JsonNbt.encode(JsonCodecs.assembly(assembly), LIMIT);
        container(stack).put("Assembly", encoded);
        entry.saved = stamp;
        entry.encoded = encoded;
        return true;
    }
}
