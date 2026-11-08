package dev.elementalcircuits.server.workshop;

import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.domain.circuit.*;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/** Server-thread plan: validate every required stack before changing the circuit or inventory. */
public final class RuneInventory {
    private RuneInventory() {}

    public static Map<String, Integer> prepare(
            ServerPlayer player, Blueprint before, Blueprint after) {
        if (player.isCreative() || ServerSettings.get("inscription.requireRunes") == 0)
            return Map.of();
        var cost = RuneMaterials.additions(before, after);
        for (var entry : cost.entrySet()) {
            var item = RuneEngraving.RUNES.get(entry.getKey()).get();
            if (MaterialInventory.count(player, item) < entry.getValue())
                throw new IllegalArgumentException(
                        Component.translatable(
                                        "message.elementalcircuits.missing_rune",
                                        item.getDescription(),
                                        entry.getValue())
                                .getString());
        }
        return cost;
    }

    public static void consume(ServerPlayer player, Map<String, Integer> cost) {
        for (var entry : cost.entrySet()) {
            int remaining = entry.getValue();
            var item = RuneEngraving.RUNES.get(entry.getKey()).get();
            for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
                var stack = player.getInventory().getItem(i);
                if (!stack.is(item)) continue;
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        if (!cost.isEmpty()) player.getInventory().setChanged();
    }
}
