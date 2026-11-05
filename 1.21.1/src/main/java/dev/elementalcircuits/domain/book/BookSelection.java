package dev.elementalcircuits.domain.book;

import dev.elementalcircuits.domain.circuit.CircuitAssembly;

import java.util.*;

/** Selects a stable slot without touching any instance, resource, clock or control state. */
public final class BookSelection {
    private BookSelection() {}

    public record Choice(UUID carrier, int slot) {
        public Choice {
            if (carrier == null || slot < 1 || slot > 65534)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.book._book_selection.001"));
        }
    }

    public static Choice cycle(List<Choice> choices, Choice current, int delta) {
        if ((delta != -1 && delta != 1)
                || choices == null
                || choices.isEmpty()
                || choices.size() > 8
                || new HashSet<>(choices).size() != choices.size())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.book._book_selection.002"));
        int index = choices.indexOf(current);
        if (index < 0)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.book._book_selection.003"));
        return choices.get(Math.floorMod(index + delta, choices.size()));
    }

    public static int cycle(CircuitAssembly a, int delta) {
        if (delta != -1 && delta != 1)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.book._book_selection.004"));
        var ids = a.slots();
        int next = ids.get(Math.floorMod(ids.indexOf(a.selected()) + delta, ids.size()));
        a.select(next);
        return next;
    }
}
