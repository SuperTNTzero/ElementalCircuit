package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.adapter.config.ServerSettings;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.UUID;

/** Observes loaded world state. Never simulates or commits inventory operations. */
public final class WorldProbe {
    private WorldProbe() {}

    public static boolean sample(
            ServerLevel level, UUID carrier, int slot, Vec3 origin, Vec3 direction, int mode) {
        if (mode == 2 || mode == 3) {
            boolean active = WorldConnections.get(level).active(carrier, slot);
            return mode == 2 ? active : !active;
        }
        if (mode < 0 || mode > 3 || direction.lengthSqr() < .0001) return false;
        Vec3 end = origin.add(direction.normalize().scale(ServerSettings.count("probe.range")));
        if (!SpellEffects.loadedBetween(level, origin, end)) return false;
        var hit =
                level.clip(
                        new ClipContext(
                                origin,
                                end,
                                ClipContext.Block.OUTLINE,
                                ClipContext.Fluid.NONE,
                                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) return false;
        if (mode == 0) return true;
        var inventory =
                level.getCapability(
                        Capabilities.ItemHandler.BLOCK, hit.getBlockPos(), hit.getDirection());
        int maximum = ServerSettings.count("automation.maxContainerSlots");
        return full(inventory, maximum);
    }

    static boolean full(net.neoforged.neoforge.items.IItemHandler inventory, int maximum) {
        int slots = inventory == null ? 0 : inventory.getSlots();
        if (slots <= 0 || slots > maximum) return false;
        // "Full" has a stable item-independent meaning: every slot occupied to its effective limit.
        for (int i = 0; i < slots; i++) {
            var stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()
                    || stack.getCount()
                            < Math.min(stack.getMaxStackSize(), inventory.getSlotLimit(i)))
                return false;
        }
        return true;
    }
}
