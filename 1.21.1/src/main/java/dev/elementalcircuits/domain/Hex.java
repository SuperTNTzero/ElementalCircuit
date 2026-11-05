package dev.elementalcircuits.domain;

import dev.elementalcircuits.domain.circuit.CircuitBounds;

public record Hex(int q, int r) {
    public Hex {
        if (Math.abs((long) q) > CircuitBounds.BOARD + 1
                || Math.abs((long) r) > CircuitBounds.BOARD + 1)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get("text.elementalcircuits.domain._hex.001"));
    }

    public int distance(Hex other) {
        return (Math.abs(q - other.q) + Math.abs(r - other.r) + Math.abs(q + r - other.q - other.r))
                / 2;
    }

    public boolean inside(int radius) {
        return Math.max(Math.max(Math.abs(q), Math.abs(r)), Math.abs(q + r)) <= radius;
    }
}
