package dev.elementalcircuits.domain.logic;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.circuit.CircuitBounds;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.TickCircuit;

import java.util.*;

/** Detached state for observers. Contains no VM or mutable state collection. */
public record LogicObservation(int version, List<Node> nodes) {
    public record Node(
            int id, Rune.Kind kind, int count, int phase, boolean latched, int parameter) {}

    public LogicObservation {
        if (version != 1 || nodes.size() > CircuitBounds.RUNES)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.logic._logic_observation.001"));
        nodes = List.copyOf(nodes);
    }

    public static LogicObservation inspect(TickCircuit circuit) {
        return new LogicObservation(
                1,
                circuit.definition().runes().stream()
                        .filter(r -> RuneLogic.sequential(r.kind()))
                        .map(
                                r -> {
                                    var s = circuit.logic(r.id());
                                    return new Node(
                                            r.id(),
                                            r.kind(),
                                            s.count(),
                                            s.phase(),
                                            s.latched(),
                                            (int) (r.value() / Fixed.ONE));
                                })
                        .toList());
    }

    public Node find(int id) {
        return nodes.stream().filter(n -> n.id() == id).findFirst().orElse(null);
    }
}
