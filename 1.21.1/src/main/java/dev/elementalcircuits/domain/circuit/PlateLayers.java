package dev.elementalcircuits.domain.circuit;

import dev.elementalcircuits.domain.casting.TriggerBinding;

import java.util.*;

/** Immutable overlapping definitions on one physical plate; only the root owns a live VM. */
public final class PlateLayers {
    public record Layer(int id, Blueprint blueprint) {}

    public record State(
            int version, int nextId, List<Layer> layers, List<TriggerBinding> bindings) {
        public State {
            layers = List.copyOf(layers);
            bindings = List.copyOf(bindings);
        }
    }

    private final Blueprint root;
    private final State state;

    public PlateLayers(Blueprint root) {
        this(root, new State(1, 2, List.of(), List.of()));
    }

    public PlateLayers(Blueprint root, State state) {
        this.root = Objects.requireNonNull(root);
        this.state = Objects.requireNonNull(state);
        if (state.version() != 1
                || state.nextId() < 2
                || state.nextId() > 65535
                || state.layers().size() > 16
                || state.bindings().size() > 17)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._plate_layers.001"));
        Set<Integer> ids = new HashSet<>();
        ids.add(1);
        for (var l : state.layers())
            if (l.id() < 2
                    || l.id() >= state.nextId()
                    || !ids.add(l.id())
                    || l.blueprint() == null
                    || !l.blueprint().rules().equals(root.rules()))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._plate_layers.002"));
        Set<Integer> sources = new HashSet<>();
        for (var t : state.bindings()) {
            if (!ids.contains(t.source()) || !ids.contains(t.target()) || !sources.add(t.source()))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._plate_layers.003"));
            if (definition(t.target()).runes().stream().noneMatch(r -> r.kind() == Rune.Kind.INPUT))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._plate_layers.004"));
        }
    }

    public State state() {
        return state;
    }

    public List<Integer> ids() {
        List<Integer> ids = new ArrayList<>();
        ids.add(1);
        state.layers().forEach(l -> ids.add(l.id()));
        return List.copyOf(ids);
    }

    public Blueprint definition(int id) {
        if (id == 1) return root;
        return state.layers().stream()
                .filter(l -> l.id() == id)
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        dev.elementalcircuits.text.Texts.get(
                                                "text.elementalcircuits.domain.circuit._plate_layers.005")))
                .blueprint();
    }

    public TriggerBinding trigger(int id) {
        definition(id);
        return state.bindings().stream().filter(t -> t.source() == id).findFirst().orElse(null);
    }

    public PlateLayers add(int limit) {
        if (limit < 1 || limit > 16 || state.layers().size() >= limit || state.nextId() >= 65535)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._plate_layers.006"));
        var layers = new ArrayList<>(state.layers());
        layers.add(new Layer(state.nextId(), Blueprint.empty(root.rules())));
        return new PlateLayers(root, new State(1, state.nextId() + 1, layers, state.bindings()));
    }

    public PlateLayers replace(int id, Blueprint definition) {
        definition(id);
        if (id == 1) return withRoot(definition);
        var layers =
                state.layers().stream()
                        .map(l -> l.id() == id ? new Layer(id, definition) : l)
                        .toList();
        return new PlateLayers(root, new State(1, state.nextId(), layers, state.bindings()));
    }

    public PlateLayers withRoot(Blueprint root) {
        var children =
                state.layers().stream()
                        .map(
                                l ->
                                        new Layer(
                                                l.id(),
                                                new Blueprint(
                                                        l.blueprint().version(),
                                                        root.rules(),
                                                        l.blueprint().runes(),
                                                        l.blueprint().routes(),
                                                        l.blueprint().continuous())))
                        .toList();
        return new PlateLayers(root, new State(1, state.nextId(), children, state.bindings()));
    }

    public PlateLayers configure(int source, TriggerBinding binding) {
        definition(source);
        if (binding != null && binding.source() != source)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._plate_layers.007"));
        var bindings =
                new ArrayList<>(
                        state.bindings().stream().filter(t -> t.source() != source).toList());
        if (binding != null) bindings.add(binding);
        bindings.sort(Comparator.comparingInt(TriggerBinding::source));
        return new PlateLayers(root, new State(1, state.nextId(), state.layers(), bindings));
    }

    public PlateLayers remove(int id) {
        definition(id);
        if (id == 1)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._plate_layers.008"));
        if (state.bindings().stream().anyMatch(t -> t.source() == id || t.target() == id))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._plate_layers.009"));
        return new PlateLayers(
                root,
                new State(
                        1,
                        state.nextId(),
                        state.layers().stream().filter(l -> l.id() != id).toList(),
                        state.bindings()));
    }

    public boolean empty() {
        return state.nextId() == 2 && state.layers().isEmpty() && state.bindings().isEmpty();
    }
}
