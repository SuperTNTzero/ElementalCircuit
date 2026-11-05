package dev.elementalcircuits.client.editor;

import dev.elementalcircuits.adapter.resources.LogicResources;
import dev.elementalcircuits.adapter.resources.ProjectileResources;
import dev.elementalcircuits.adapter.resources.SecondaryResources;
import dev.elementalcircuits.client.text.UiText;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.CircuitBounds;
import dev.elementalcircuits.domain.circuit.HexRouter;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.Mixture;

import java.util.*;

/** Detached interaction state. Renderers receive an immutable View and cannot edit this model. */
public final class EditorModel {
    public enum Tool {
        SELECT,
        PLACE,
        ROUTE
    }

    public final EditorDraft draft;
    public final RuneParameterEdit parameters = new RuneParameterEdit();
    public final EnumSet<RuneCatalog.Group> expanded = EnumSet.of(RuneCatalog.Group.FLOW);
    public Tool tool = Tool.SELECT;
    public Rune.Kind kind = Rune.Kind.INPUT;
    public Element element = Element.FIRE;
    private int dragging = -1;
    public Route.Payload payload = Route.Payload.MANA;
    public int sourcePort;
    public int selected = -1, paletteOffset, parameterScroll;
    public boolean components;
    private int source = -1;
    private final List<Hex> path = new ArrayList<>();
    public String message = "";
    private View cachedView;
    private Hex cachedHover;
    private int cachedSource;
    private int cachedPort;
    private List<Hex> cachedPath = List.of();

    public EditorModel(Blueprint blueprint) {
        draft = new EditorDraft(blueprint);
    }

    public Rune selected() {
        return draft.get().runes().stream()
                .filter(r -> r.id() == selected)
                .findFirst()
                .orElse(null);
    }

    public void cancel() {
        source = -1;
        path.clear();
        dragging = -1;
    }

    public boolean drawing() {
        return source >= 0;
    }

    public void beginRoute(int id, int port) {
        Rune rune = draft.get().rune(id);
        if (port < 0 || port >= rune.kind().outputPorts(payload))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._editor_model.001"));
        cancel();
        sourcePort = port;
        source = id;
        path.add(rune.cell());
        tool = Tool.ROUTE;
        components = false;
        message =
                dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.editor._editor_model.002")
                        + rune.kind().outputLabel(payload, port);
    }

    public void choose(Rune.Kind kind) {
        this.kind = kind;
        tool = Tool.PLACE;
        components = false;
        parameterScroll = 0;
        cancel();
    }

    public void choose(Rune.Kind kind, Element element) {
        choose(kind);
        if (element != null) this.element = element;
    }

    public void beginDrag() {
        if (tool == Tool.SELECT && selected > 0) dragging = selected;
    }

    public boolean dragging() {
        return dragging >= 0;
    }

    public void drop(Hex target) {
        int id = dragging;
        dragging = -1;
        if (id >= 0 && target != null) {
            draft.move(id, target);
            message =
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._editor_model.003");
        }
    }

    public void toggle(RuneCatalog.Group group) {
        if (!expanded.remove(group)) expanded.add(group);
    }

    public boolean deleteRoute(Route route) {
        if (tool != Tool.ROUTE
                || components
                || drawing()
                || route == null
                || route.payload() != payload
                || !draft.get().routes().contains(route)) return false;
        var b = draft.get();
        var routes = new ArrayList<>(b.routes());
        routes.remove(route);
        draft.set(new Blueprint(b.version(), b.rules(), b.runes(), routes, b.continuous()));
        message =
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.editor._editor_model.004");
        return true;
    }

    public void click(Hex cell) {
        Rune rune = draft.at(cell);
        if (tool == Tool.PLACE) {
            if (rune != null)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.editor._editor_model.005"));
            draft.place(kind, element, cell);
            selected = draft.at(cell).id();
            return;
        }
        if (tool == Tool.SELECT) {
            int next = rune == null ? -1 : rune.id();
            if (next != selected) parameterScroll = 0;
            selected = next;
            return;
        }
        if (source < 0) {
            if (rune == null)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.editor._editor_model.006"));
            beginRoute(rune.id(), sourcePort);
            return;
        }
        if (path.get(path.size() - 1).equals(cell)) return;
        List<Hex> completed = preview(cell);
        if (rune != null) {
            draft.route(source, rune.id(), payload, completed, sourcePort);
            Route route = draft.get().routes().get(draft.get().routes().size() - 1);
            message = routeHint(route, draft.get().rules());
            cancel();
        } else {
            path.clear();
            path.addAll(completed);
        }
    }

    public List<Hex> preview(Hex target) {
        if (!drawing() || target == null || target.equals(path.get(path.size() - 1)))
            return List.copyOf(path);
        List<Hex> result = new ArrayList<>(path);
        List<Hex> segment = HexRouter.segment(draft.get(), path.get(path.size() - 1), target, path);
        result.addAll(segment.subList(1, segment.size()));
        if (result.size() > CircuitBounds.PATH)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._editor_model.007"));
        return List.copyOf(result);
    }

    public View view(Hex hover) {
        Hex relevantHover =
                drawing() || !components && (tool == Tool.PLACE || dragging()) ? hover : null;
        if (cachedView != null
                && cachedView.board() == draft.get()
                && cachedView.tool() == tool
                && cachedView.kind() == kind
                && cachedView.element() == element
                && cachedView.payload() == payload
                && cachedView.selected() == selected
                && cachedView.components() == components
                && cachedView.dragging() == dragging
                && Objects.equals(cachedView.message(), message)
                && Objects.equals(cachedHover, relevantHover)
                && cachedSource == source
                && cachedPort == sourcePort
                && cachedPath.equals(path)) return cachedView;
        List<Hex> preview = List.copyOf(path);
        boolean valid = true;
        String hint = "";
        if (drawing() && hover != null)
            try {
                preview = preview(hover);
                if (preview.size() > 1)
                    hint =
                            routeHint(
                                    new Route(
                                            source,
                                            source == 1 ? 2 : 1,
                                            payload,
                                            preview,
                                            sourcePort),
                                    draft.get().rules());
            } catch (IllegalArgumentException e) {
                valid = false;
                hint = e.getMessage();
            }
        Rune ghost = null;
        Blueprint moved = null;
        boolean ghostValid = true;
        String label = "";
        if (!components && hover != null && (tool == Tool.PLACE || dragging())) {
            Rune original =
                    dragging()
                            ? draft.get().rune(dragging)
                            : Rune.create(
                                    1024,
                                    kind,
                                    hover,
                                    LogicResources.defaults(),
                                    ProjectileResources.rules(),
                                    SecondaryResources.rules());
            ghost =
                    new Rune(
                            original.id(),
                            original.kind(),
                            hover,
                            dragging() ? original.element() : element,
                            original.value(),
                            original.on(),
                            original.signalControl(),
                            original.weights(),
                            original.storage(),
                            original.filter());
            label =
                    ghost.kind() == Rune.Kind.CONVERT
                            ? UiText.string(
                                    "rune.elementalcircuits.convert",
                                    UiText.element(ghost.element()))
                            : RuneCatalog.title(ghost.kind());
            try {
                if (dragging()) moved = draft.moved(dragging, hover);
                else draft.placed(kind, element, hover);
            } catch (IllegalArgumentException e) {
                ghostValid = false;
                label += " · " + e.getMessage();
            }
        }
        cachedHover = relevantHover;
        cachedSource = source;
        cachedPort = sourcePort;
        cachedPath = List.copyOf(path);
        return cachedView =
                new View(
                        draft.get(),
                        tool,
                        kind,
                        element,
                        payload,
                        selected,
                        preview,
                        valid,
                        hint,
                        message,
                        components,
                        ghost,
                        ghostValid,
                        dragging,
                        moved,
                        label);
    }

    public static String routeHint(Route route, Rules rules) {
        if (route.payload() == Route.Payload.SIGNAL)
            return dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._editor_model.008")
                    + (route.sourcePort() + 1)
                    + " · "
                    + route.length()
                    + dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._editor_model.009");
        double loss =
                100
                        * (1
                                - route.transmit(Mixture.pure(Fixed.ONE), rules).total()
                                        / (double) Fixed.ONE);
        return String.format(
                Locale.ROOT,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.editor._editor_model.010"),
                route.length(),
                loss);
    }

    public record View(
            Blueprint board,
            Tool tool,
            Rune.Kind kind,
            Element element,
            Route.Payload payload,
            int selected,
            List<Hex> preview,
            boolean valid,
            String routeHint,
            String message,
            boolean components,
            Rune ghost,
            boolean ghostValid,
            int dragging,
            Blueprint moved,
            String ghostLabel) {
        public View {
            preview = List.copyOf(preview);
        }
    }
}
