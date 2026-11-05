package dev.elementalcircuits.server.circuit;

import dev.elementalcircuits.block.CircuitBlockEntity;
import dev.elementalcircuits.domain.Fixed;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** The block and tool share one validated control boundary. Selection never stops other slots. */
public final class FixedCircuitControl {
    public enum Action {
        TOGGLE,
        NEXT_SLOT,
        CHARGE
    }

    private FixedCircuitControl() {}

    public static Component apply(ServerPlayer player, CircuitBlockEntity block, Action action) {
        if (!block.allowed(player))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.circuit._fixed_circuit_control.001"));
        var assembly = block.assembly();
        if (action == Action.CHARGE) {
            return Component.translatable(
                    "control.elementalcircuits.charged",
                    Fixed.display(block.charge(player)),
                    Fixed.display(block.available()));
        }
        if (action == Action.NEXT_SLOT) {
            var slots = assembly.slots();
            assembly.select(slots.get((slots.indexOf(assembly.selected()) + 1) % slots.size()));
        } else {
            var circuit = assembly.circuit(assembly.selected());
            if (circuit.powered()) circuit.stop();
            else {
                if (assembly.boardBroken(assembly.selected()))
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.circuit._fixed_circuit_control.002"));
                if (!circuit.start())
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.circuit._fixed_circuit_control.003"));
            }
        }
        CarrierService.persist(block.carrier());
        block.setChanged();
        return Component.translatable(
                "control.elementalcircuits.state",
                assembly.selected(),
                Component.translatable(
                        assembly.circuit(assembly.selected()).powered()
                                ? "control.elementalcircuits.on"
                                : "control.elementalcircuits.off"));
    }
}
