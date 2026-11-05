package dev.elementalcircuits.client.editor;

import dev.elementalcircuits.adapter.resources.LogicResources;
import dev.elementalcircuits.adapter.resources.ProjectileResources;
import dev.elementalcircuits.adapter.resources.SecondaryResources;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.HexRouter;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.combat.Element;

import java.util.*;

/** Detached editor configuration, bounded history, no references to a live VM. */
public final class EditorDraft {
    private Blueprint blueprint;
    private final Deque<Blueprint> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();

    public EditorDraft(Blueprint blueprint) {
        this.blueprint = blueprint;
    }

    public Blueprint get() {
        return blueprint;
    }

    public void set(Blueprint next) {
        if (next.equals(blueprint)) return;
        undo.addLast(blueprint);
        if (undo.size() > 32) undo.removeFirst();
        redo.clear();
        blueprint = next;
    }

    public void undo() {
        if (!undo.isEmpty()) {
            redo.addLast(blueprint);
            blueprint = undo.removeLast();
        }
    }

    public void redo() {
        if (!redo.isEmpty()) {
            undo.addLast(blueprint);
            blueprint = redo.removeLast();
        }
    }

    public void place(Rune.Kind kind, Hex cell) {
        place(kind, Element.FIRE, cell);
    }

    public void place(Rune.Kind kind, Element element, Hex cell) {
        set(placed(kind, element, cell));
    }

    public Blueprint placed(Rune.Kind kind, Element element, Hex cell) {
        if (at(cell) != null)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._editor_draft.001"));
        Blueprint source = blueprint;
        List<Rune> runes = new ArrayList<>(source.runes());
        // Sparse imported IDs may already reach Integer.MAX_VALUE. Allocate a free ID rather
        // than overflowing or making deletion permanently consume the namespace.
        Set<Integer> used = new HashSet<>();
        for (Rune existing : runes) used.add(existing.id());
        int id = 1;
        while (used.contains(id)) id++;
        Rune rune =
                Rune.create(
                        id,
                        kind,
                        cell,
                        LogicResources.defaults(),
                        ProjectileResources.rules(),
                        SecondaryResources.rules());
        runes.add(
                new Rune(
                        id,
                        kind,
                        cell,
                        element,
                        rune.value(),
                        rune.on(),
                        rune.signalControl(),
                        rune.weights(),
                        rune.storage(),
                        rune.filter()));
        return new Blueprint(
                source.version(),
                blueprint.rules(),
                runes,
                blueprint.routes(),
                blueprint.continuous());
    }

    /**
     * Move and connected-wire rerouting are one validated undo step. Failure leaves the draft
     * intact.
     */
    public Blueprint moved(int id, Hex cell) {
        Rune old = blueprint.rune(id);
        if (old.cell().equals(cell)) return blueprint;
        if (at(cell) != null)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._editor_draft.002"));
        List<Rune> runes =
                blueprint.runes().stream()
                        .map(
                                r ->
                                        r.id() != id
                                                ? r
                                                : new Rune(
                                                        id,
                                                        r.kind(),
                                                        cell,
                                                        r.element(),
                                                        r.value(),
                                                        r.on(),
                                                        r.signalControl(),
                                                        r.weights(),
                                                        r.storage(),
                                                        r.filter()))
                        .toList();
        Blueprint positions =
                new Blueprint(
                        blueprint.version(),
                        blueprint.rules(),
                        runes,
                        List.of(),
                        blueprint.continuous());
        List<Route> wires = new ArrayList<>();
        for (Route route : blueprint.routes()) {
            boolean touched =
                    route.source() == id || route.target() == id || route.path().contains(cell);
            wires.add(
                    !touched
                            ? route
                            : new Route(
                                    route.source(),
                                    route.target(),
                                    route.payload(),
                                    HexRouter.segment(
                                            positions,
                                            positions.rune(route.source()).cell(),
                                            positions.rune(route.target()).cell(),
                                            List.of()),
                                    route.sourcePort()));
        }
        return new Blueprint(
                blueprint.version(), blueprint.rules(), runes, wires, blueprint.continuous());
    }

    public void move(int id, Hex cell) {
        set(moved(id, cell));
    }

    public Rune at(Hex cell) {
        return blueprint.runes().stream()
                .filter(r -> r.cell().equals(cell))
                .findFirst()
                .orElse(null);
    }

    public void delete(int id) {
        set(
                new Blueprint(
                        blueprint.version(),
                        blueprint.rules(),
                        blueprint.runes().stream().filter(r -> r.id() != id).toList(),
                        blueprint.routes().stream()
                                .filter(e -> e.source() != id && e.target() != id)
                                .toList(),
                        blueprint.continuous()));
    }

    public void inputAmount(int id, long amount) {
        set(blueprint.withInputAmount(id, amount));
    }

    public void replace(Rune r) {
        set(
                new Blueprint(
                        blueprint.version(),
                        blueprint.rules(),
                        blueprint.runes().stream()
                                .map(old -> old.id() == r.id() ? r : old)
                                .toList(),
                        blueprint.routes(),
                        blueprint.continuous()));
    }

    public void route(int source, int target, Route.Payload payload, List<Hex> path) {
        route(source, target, payload, path, 0);
    }

    public void route(int source, int target, Route.Payload payload, List<Hex> path, int port) {
        Blueprint base = blueprint;
        List<Route> routes = new ArrayList<>(base.routes());
        routes.add(new Route(source, target, payload, path, port));
        set(
                new Blueprint(
                        base.version(),
                        base.rules(),
                        base.runes(),
                        routes,
                        blueprint.continuous()));
    }
}
