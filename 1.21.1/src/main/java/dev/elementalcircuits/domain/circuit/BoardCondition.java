package dev.elementalcircuits.domain.circuit;

import dev.elementalcircuits.domain.Fixed;

/** Physical board wear, shared by its root and layers. Quantities retain fractional durability. */
public record BoardCondition(
        int version, long maximum, long spent, boolean melted, long supplyCursor) {
    public BoardCondition {
        if (version != 1
                || maximum < Fixed.ONE
                || maximum > 1000000L * Fixed.ONE
                || spent < 0
                || spent > maximum
                || supplyCursor < 0
                || melted && spent != maximum)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._board_condition.001"));
    }

    public boolean broken() {
        return spent == maximum;
    }

    public long remaining() {
        return maximum - spent;
    }
}
