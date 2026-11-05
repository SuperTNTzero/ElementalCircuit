package dev.elementalcircuits.domain.logic;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.circuit.Rune;

import java.util.List;

/** Immutable, complete sequential state; no world clock or renderer participates. */
public record RuneLogicState(
        int count,
        int phase,
        boolean latched,
        boolean last,
        boolean armed,
        long elapsed,
        List<Transition> queue) {
    public record Transition(long due, boolean value, boolean valid) {
        public Transition {
            if (due < 1)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.logic._rune_logic_state.001"));
        }
    }

    public RuneLogicState(int count, int phase, boolean latched, boolean last, boolean armed) {
        this(count, phase, latched, last, armed, 0, List.of());
    }

    public RuneLogicState {
        if (elapsed < 0
                || queue == null
                || queue.size()
                        > dev.elementalcircuits.domain.circuit.CircuitBounds.DELAY_TICKS + 1)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.logic._rune_logic_state.002"));
        queue = SignalQueue.from(queue);
    }

    public static final RuneLogicState EMPTY = new RuneLogicState(0, 0, false, false, false);

    public static RuneLogicState initial(Rune rune) {
        return rune.kind() == Rune.Kind.TOGGLE
                ? new RuneLogicState(0, 0, rune.on(), false, false)
                : EMPTY;
    }

    public RuneLogicState suspended() {
        if (elapsed > 0 || !queue.isEmpty()) return this;
        return new RuneLogicState(count, phase, latched, false, false);
    }

    public void validate(Rune rune) {
        if (count < 0 || phase < 0 || !armed && last)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.logic._rune_logic_state.003"));
        if (rune.kind() != Rune.Kind.DELAY && (elapsed != 0 || !queue.isEmpty()))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.logic._rune_logic_state.004"));
        boolean ok =
                switch (rune.kind()) {
                    case COUNTER -> count < rune.value() / Fixed.ONE && phase == 0 && !latched;
                    case CLOCK ->
                            count == 0
                                    && phase < rune.value() / Fixed.ONE
                                    && !latched
                                    && !last
                                    && !armed;
                    case TOGGLE -> count == 0 && phase == 0;
                    case EDGE -> count == 0 && phase == 0 && !latched;
                    case DELAY ->
                            count <= 1
                                    && phase == 0
                                    && elapsed
                                            <= Long.MAX_VALUE
                                                    - dev.elementalcircuits.domain.circuit
                                                            .CircuitBounds.DELAY_TICKS
                                                    - 1;
                    default -> equals(EMPTY);
                };
        if (!ok)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.logic._rune_logic_state.005"));
        long previous = elapsed;
        for (var event : queue) {
            if (event.due() <= previous || event.due() - elapsed > rune.value() / Fixed.ONE)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.logic._rune_logic_state.006"));
            previous = event.due();
        }
    }
}
