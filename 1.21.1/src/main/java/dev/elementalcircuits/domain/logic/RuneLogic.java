package dev.elementalcircuits.domain.logic;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.circuit.Rune;

/** Pure one-step transitions for counting, periodic pulses and a toggle latch. */
public final class RuneLogic {
    public record Result(RuneLogicState state, boolean value, boolean valid) {}

    private RuneLogic() {}

    public static boolean sequential(Rune.Kind kind) {
        return kind == Rune.Kind.COUNTER
                || kind == Rune.Kind.CLOCK
                || kind == Rune.Kind.TOGGLE
                || kind == Rune.Kind.DELAY
                || kind == Rune.Kind.EDGE;
    }

    public static Result step(
            Rune rune, RuneLogicState previous, boolean high, boolean valid, boolean connected) {
        if (!sequential(rune.kind()))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.logic._rune_logic.001"));
        if (rune.kind() == Rune.Kind.DELAY) return delay(rune, previous, high, valid && connected);
        if (rune.kind() == Rune.Kind.EDGE) {
            if (!valid || !connected) return new Result(previous.suspended(), false, false);
            boolean pulse =
                    connected
                            && previous.armed()
                            && (rune.value() == 0 && !previous.last() && high
                                    || rune.value() == Fixed.ONE && previous.last() && !high
                                    || rune.value() == 2 * Fixed.ONE && previous.last() != high);
            return new Result(new RuneLogicState(0, 0, false, high, true), pulse, true);
        }
        if (!valid) return new Result(previous.suspended(), false, false);
        if (rune.kind() == Rune.Kind.CLOCK) {
            int phase = previous.phase();
            boolean pulse = false;
            if (!connected || high) {
                phase++;
                if (phase == rune.value() / Fixed.ONE) {
                    phase = 0;
                    pulse = true;
                }
            }
            return new Result(new RuneLogicState(0, phase, false, false, false), pulse, true);
        }
        boolean rise = connected && previous.armed() && !previous.last() && high;
        int count = previous.count();
        boolean latched = previous.latched(), pulse = false;
        if (rune.kind() == Rune.Kind.COUNTER && rise) {
            count++;
            if (count == rune.value() / Fixed.ONE) {
                count = 0;
                pulse = true;
            }
        }
        if (rune.kind() == Rune.Kind.TOGGLE && rise) latched = !latched;
        return new Result(
                new RuneLogicState(count, 0, latched, high, true),
                rune.kind() == Rune.Kind.COUNTER ? pulse : latched,
                true);
    }

    private static Result delay(Rune rune, RuneLogicState previous, boolean high, boolean valid) {
        // Queue transitions, not every sample. Space depends on changes during the delay horizon.
        long elapsed = Math.addExact(previous.elapsed(), 1);
        var queue = SignalQueue.from(previous.queue());
        if (previous.elapsed() == 0
                || previous.armed() != valid
                || valid && previous.last() != high)
            queue =
                    queue.append(
                            new RuneLogicState.Transition(
                                    Math.addExact(elapsed, rune.value() / Fixed.ONE),
                                    valid && high,
                                    valid));
        queue = queue.ready();
        boolean value = previous.latched(), outputValid = previous.count() == 1;
        if (!queue.isEmpty() && queue.getFirst().due() <= elapsed) {
            var event = queue.getFirst();
            queue = queue.drop();
            value = event.value();
            outputValid = event.valid();
        }
        return new Result(
                new RuneLogicState(
                        outputValid ? 1 : 0, 0, value, valid && high, valid, elapsed, queue),
                value,
                outputValid);
    }
}
