package dev.elementalcircuits.server.circuit;

import dev.elementalcircuits.adapter.persistence.CarrierRepository;
import dev.elementalcircuits.block.CircuitBlockEntity;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.CircuitAssembly;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.server.workshop.MaterialInventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;

import java.util.UUID;

/**
 * Compatibility entry points for items and packets. State, commands, observation and runtime have
 * separate owners.
 */
public final class CarrierService {
    private CarrierService() {}

    public static UUID identity(ItemStack stack) {
        return CarrierRepository.identity(stack);
    }

    public static CircuitAssembly assembly(ItemStack stack) {
        return CarrierRepository.load(stack);
    }

    public static TickCircuit get(ItemStack stack) {
        var a = assembly(stack);
        return a.circuit(a.selected());
    }

    public static void persist(ItemStack stack) {
        CarrierRepository.commit(stack);
    }

    public static void tick(ItemStack stack, ServerPlayer player) {
        WorldCircuits.enqueue(stack, player);
    }

    public static void tickDropped(ItemStack stack, ItemEntity entity) {
        try {
            var a = assembly(stack);
            if (a.powered()) {
                a.stopAll();
                persist(stack);
            }
        } catch (RuntimeException e) {
            CarrierFaults.report(stack, null);
        }
    }

    public static void corrupt(ItemStack stack, ServerPlayer player) {
        CarrierFaults.report(stack, player);
    }

    public static void start(ServerPlayer player, ItemStack stack) {
        CarrierCommands.start(player, stack);
    }

    public static void open(ServerPlayer player) {
        CarrierEditor.open(player);
    }

    public static void open(ServerPlayer player, CircuitBlockEntity block) {
        CarrierEditor.open(player, block);
    }

    public static void sync(ServerPlayer player, ItemStack stack, boolean open, String message) {
        CarrierEditor.sync(player, stack, open, message);
    }

    public static void request(ServerPlayer player, UUID id, int action, String payload) {
        CarrierCommands.request(player, id, action, payload);
    }

    public static void request(ServerPlayer player, UUID id, int slot, int action, String payload) {
        CarrierCommands.request(player, id, slot, action, payload);
    }

    public static Item plate(Rules.Material material) {
        return MaterialInventory.plate(material);
    }

    public static void forget(UUID player) {
        CarrierCommands.forget(player);
    }
}
