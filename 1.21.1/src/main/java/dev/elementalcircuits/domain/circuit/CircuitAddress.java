package dev.elementalcircuits.domain.circuit;

import java.util.UUID;

public record CircuitAddress(UUID carrier, int slot, int rune) {
    public CircuitAddress {
        if (carrier == null || slot < 1 || slot > 65535 || rune < 1)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._circuit_address.001"));
    }
}
