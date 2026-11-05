package dev.elementalcircuits.server.circuit;

import dev.elementalcircuits.application.runtime.CircuitHost;
import dev.elementalcircuits.application.runtime.CircuitScheduler;
import dev.elementalcircuits.block.*;
import dev.elementalcircuits.server.mana.WorldManaRefunds;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** One world boundary: deliver old transports, advance all owners, then publish new transports. */
public final class WorldCircuits {
    private record Job(ItemStack stack, ServerPlayer player) {}

    private static final Map<ServerLevel, List<Job>> WAITING = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, CircuitBlockEntity>> BLOCKS =
            new WeakHashMap<>();

    private WorldCircuits() {}

    public static void enqueue(ItemStack stack, ServerPlayer player) {
        WAITING.computeIfAbsent(player.serverLevel(), l -> new ArrayList<>())
                .add(new Job(stack, player));
    }

    public static void register(CircuitBlockEntity block) {
        if (block.getLevel() instanceof ServerLevel level)
            BLOCKS.computeIfAbsent(level, l -> new HashMap<>()).put(block.getBlockPos(), block);
    }

    public static void unregister(CircuitBlockEntity block) {
        if (block.getLevel() instanceof ServerLevel level) {
            var map = BLOCKS.get(level);
            if (map != null) map.remove(block.getBlockPos(), block);
        }
    }

    public static void unload(ServerLevel level) {
        WAITING.remove(level);
        BLOCKS.remove(level);
    }

    public static List<CircuitBlockEntity> blocks(ServerLevel level) {
        var map = BLOCKS.get(level);
        return map == null
                ? List.of()
                : map.values().stream()
                        .filter(b -> !b.isRemoved() && level.hasChunkAt(b.getBlockPos()))
                        .toList();
    }

    public static CircuitBlockEntity block(ServerLevel level, UUID id) {
        CircuitBlockEntity found = null;
        for (var b : blocks(level))
            try {
                if (b.assembly().identity().equals(id)) {
                    if (found != null) return null;
                    found = b;
                }
            } catch (RuntimeException ignored) {
            }
        return found;
    }

    private static final CircuitScheduler SCHEDULER = new CircuitScheduler();

    public static void tick(ServerLevel level) {
        List<CircuitHost> hosts = new ArrayList<>();
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Job job : WAITING.getOrDefault(level, List.of()))
            if (seen.add(job.stack))
                hosts.add(MinecraftCircuitHost.carried(level, job.stack, job.player));
        WAITING.remove(level);
        for (var block : blocks(level))
            try {
                if (seen.add(block.carrier())) hosts.add(MinecraftCircuitHost.fixed(level, block));
            } catch (RuntimeException ignored) {
            }
        SCHEDULER.tick(hosts, WorldCircuitLinks.get(level));
        WorldManaRefunds.get(level).tick(level);
    }

    /**
     * Compatibility seam for existing integrations; loaded fixed hosts never depend on online
     * players.
     */
    public static void tick(
            ServerLevel level, java.util.function.Function<UUID, ServerPlayer> onlineOwners) {
        tick(level);
    }
}
