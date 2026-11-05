package dev.elementalcircuits.domain.circuit;

/** Invocation intent; does not rewrite the saved blueprint's supply preference. */
public enum SupplyMode {
    CONFIGURED,
    PULSE;

    /**
     * Persistent connections must be deliberately configured; other gestures remain single-shot.
     */
    public static SupplyMode gesture(Blueprint board) {
        return board.continuous()
                        && board.runes().stream().anyMatch(r -> r.kind() == Rune.Kind.CONTACT)
                ? CONFIGURED
                : PULSE;
    }

    public boolean continuous(Blueprint board) {
        return this == CONFIGURED && board.continuous();
    }
}
