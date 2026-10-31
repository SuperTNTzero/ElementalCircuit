package dev.elementalcircuits.domain;

import java.util.List;

public record Route(int source, int target, Payload payload, List<Hex> path) {
    public enum Payload { MANA, SIGNAL }
    public Route {
        if (source == target || source < 1 || target < 1 || payload == null || path == null || path.size() < 2 || path.size() > 128)
            throw new IllegalArgumentException("非法线路");
        path = List.copyOf(path);
    }
    public int length() { return path.size() - 1; }
    /** Same rounding order for previews and authoritative resource transfer. */
    public Mixture transmit(Mixture mana,Rules rules) {
        Mixture after=mana.retain(rules.coupling());
        for(int step=0;step<length();step++)after=after.retain(rules.retention());
        return after.retain(rules.coupling());
    }
}
