package dev.elementalcircuits.application.observation;

import dev.elementalcircuits.domain.book.BookSelection;
import dev.elementalcircuits.domain.circuit.CircuitAssembly;
import dev.elementalcircuits.domain.circuit.CircuitBounds;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.combat.Element;

import java.util.*;

/** A wheel session belongs to the wand; entries retain their physical source and local slot. */
public record CircuitSelection(
        UUID identity, UUID selectedCarrier, boolean book, int selected, List<Slot> slots) {
    public record Slot(
            UUID carrier,
            boolean book,
            int id,
            Rules.Material material,
            boolean powered,
            int runes,
            Element element) {
        public Slot {
            if (carrier == null
                    || id < 1
                    || id > 65534
                    || material == null
                    || element == null
                    || runes < 0
                    || runes > CircuitBounds.RUNES)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.application.observation._circuit_selection.001"));
        }

        public BookSelection.Choice choice() {
            return new BookSelection.Choice(carrier, id);
        }
    }

    public CircuitSelection {
        if (identity == null
                || selectedCarrier == null
                || slots == null
                || slots.isEmpty()
                || slots.size() > 8)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.application.observation._circuit_selection.002"));
        slots = List.copyOf(slots);
        if (slots.stream().map(Slot::choice).distinct().count() != slots.size()
                || slots.stream().map(Slot::carrier).distinct().count() > 2
                || slots.stream()
                        .noneMatch(
                                s ->
                                        s.carrier().equals(selectedCarrier)
                                                && s.id() == selected
                                                && s.book() == book))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.application.observation._circuit_selection.003"));
        for (var source : slots.stream().map(Slot::carrier).distinct().toList()) {
            var group = slots.stream().filter(s -> s.carrier().equals(source)).toList();
            if (group.size() > 4 || group.stream().map(Slot::book).distinct().count() != 1)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.application.observation._circuit_selection.004"));
        }
    }

    public boolean selected(Slot slot) {
        return slot.carrier().equals(selectedCarrier) && slot.id() == selected;
    }

    public Slot current() {
        return slots.stream().filter(this::selected).findFirst().orElseThrow();
    }

    private static List<Slot> slots(CircuitAssembly a, boolean book) {
        return a.slots().stream()
                .map(
                        id -> {
                            var c = a.circuit(id);
                            var b = c.definition();
                            return new Slot(
                                    a.identity(),
                                    book,
                                    id,
                                    b.rules().material(),
                                    c.powered(),
                                    b.runes().size(),
                                    b.runes().stream()
                                            .filter(r -> r.kind() == Rune.Kind.CONVERT)
                                            .map(Rune::element)
                                            .findFirst()
                                            .orElse(Element.PURE));
                        })
                .toList();
    }

    public static CircuitSelection inspect(CircuitAssembly a, boolean book) {
        return new CircuitSelection(a.identity(), a.identity(), book, a.selected(), slots(a, book));
    }

    public static CircuitSelection inspect(
            CircuitAssembly wand, CircuitAssembly book, UUID active) {
        var entries = new ArrayList<>(slots(wand, false));
        if (book != null) {
            if (book.identity().equals(wand.identity()))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.application.observation._circuit_selection.005"));
            entries.addAll(slots(book, true));
        }
        boolean fromBook = book != null && book.identity().equals(active);
        return new CircuitSelection(
                wand.identity(),
                fromBook ? book.identity() : wand.identity(),
                fromBook,
                fromBook ? book.selected() : wand.selected(),
                entries);
    }
}
