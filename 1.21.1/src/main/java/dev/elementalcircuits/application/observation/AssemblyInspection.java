package dev.elementalcircuits.application.observation;

import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.TriggerBinding;
import dev.elementalcircuits.domain.circuit.CircuitAddress;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;

import java.util.*;

/** Detached management observation, never a live assembly or mutable VM. */
public record AssemblyInspection(
        int selected,
        int maxSlots,
        boolean fixed,
        long charge,
        long capacity,
        List<Slot> slots,
        List<Port> ports,
        List<Link> links,
        List<Stock> stock,
        List<TriggerBinding> triggers,
        List<Layer> layers,
        int depthLimit) {
    public record Layer(int id, int runes, TriggerBinding trigger) {}

    public AssemblyInspection(
            int selected,
            int maxSlots,
            boolean fixed,
            long charge,
            long capacity,
            List<Slot> slots,
            List<Port> ports,
            List<Link> links,
            List<Stock> stock,
            List<TriggerBinding> triggers) {
        this(
                selected, maxSlots, fixed, charge, capacity, slots, ports, links, stock, triggers,
                List.of(), 0);
    }

    public AssemblyInspection(
            int selected,
            int maxSlots,
            boolean fixed,
            long charge,
            long capacity,
            List<Slot> slots,
            List<Port> ports,
            List<Link> links,
            List<Stock> stock) {
        this(selected, maxSlots, fixed, charge, capacity, slots, ports, links, stock, List.of());
    }

    public record Slot(
            int id,
            Rules.Material material,
            boolean powered,
            long held,
            long boardRemaining,
            long boardMaximum,
            long boardCapacity,
            boolean melted) {
        public Slot(int id, Rules.Material material, boolean powered, long held) {
            this(id, material, powered, held, 0, 0, 0, false);
        }
    }

    public record Port(CircuitAddress address, Rune.Kind kind, String label) {}

    public record Link(int id, boolean external, String label, long held) {}

    public record Stock(Rules.Material material, int count) {}

    public AssemblyInspection {
        slots = List.copyOf(slots);
        ports = List.copyOf(ports);
        links = List.copyOf(links);
        stock = List.copyOf(stock);
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
        layers = layers == null ? List.of() : List.copyOf(layers);
    }
}
