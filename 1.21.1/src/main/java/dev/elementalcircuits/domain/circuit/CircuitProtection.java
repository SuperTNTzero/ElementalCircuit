package dev.elementalcircuits.domain.circuit;

/** Called after real intake, before any rune transfer or world output. */
@FunctionalInterface
public interface CircuitProtection {
    CircuitProtection NONE = circuit -> true;

    boolean admit(TickCircuit circuit);
}
