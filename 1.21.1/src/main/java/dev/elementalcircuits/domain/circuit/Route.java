package dev.elementalcircuits.domain.circuit;

import dev.elementalcircuits.domain.Hex;
import dev.elementalcircuits.domain.mana.Mixture;

import java.util.List;

public record Route(int source, int target, Payload payload, List<Hex> path, int sourcePort) {
    public Route(int source, int target, Payload payload, List<Hex> path) {
        this(source, target, payload, path, 0);
    }

    public enum Payload {
        MANA,
        SIGNAL
    }

    public Route {
        if (source == target
                || source < 1
                || sourcePort < 0
                || sourcePort > 2
                || target < 1
                || payload == null
                || path == null
                || path.size() < 2
                || path.size() > CircuitBounds.PATH)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._route.001"));
        path = List.copyOf(path);
    }

    public int length() {
        return path.size() - 1;
    }

    /** Same rounding order for previews and authoritative resource transfer. */
    public Mixture transmit(Mixture mana, Rules rules) {
        return mana.transmit(
                rules.casting().retention(rules.coupling()),
                rules.casting().retention(rules.retention()),
                length());
    }
}
