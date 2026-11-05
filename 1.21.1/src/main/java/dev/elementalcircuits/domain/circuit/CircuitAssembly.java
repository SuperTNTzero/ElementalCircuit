package dev.elementalcircuits.domain.circuit;

import dev.elementalcircuits.domain.casting.CastContext;
import dev.elementalcircuits.domain.casting.CastingRules;
import dev.elementalcircuits.domain.casting.ReleasePermit;
import dev.elementalcircuits.domain.casting.ShapeRules;
import dev.elementalcircuits.domain.casting.TriggerBinding;
import dev.elementalcircuits.domain.mana.ManaSource;

import java.util.*;

/** Stable slots share one supply but retain independent definitions and complete VM state. */
public final class CircuitAssembly {
    public record Slot(int id, CircuitSnapshot circuit, BoardCondition board) {
        public Slot(int id, CircuitSnapshot circuit) {
            this(id, circuit, null);
        }
    }

    public record Overlay(int slot, PlateLayers.State state) {}

    public record Snapshot(
            int version,
            UUID identity,
            AssemblyRules rules,
            int selected,
            int nextSlot,
            List<Slot> slots,
            CircuitLinks.Snapshot links,
            List<TriggerBinding> triggers,
            List<Overlay> overlays) {
        public Snapshot {
            slots = List.copyOf(slots);
            triggers = List.copyOf(triggers);
            overlays = List.copyOf(overlays);
        }

        public Snapshot(
                int version,
                UUID identity,
                AssemblyRules rules,
                int selected,
                int nextSlot,
                List<Slot> slots,
                CircuitLinks.Snapshot links) {
            this(version, identity, rules, selected, nextSlot, slots, links, List.of(), List.of());
        }

        public Snapshot(
                int version,
                UUID identity,
                AssemblyRules rules,
                int selected,
                int nextSlot,
                List<Slot> slots,
                CircuitLinks.Snapshot links,
                List<TriggerBinding> triggers) {
            this(version, identity, rules, selected, nextSlot, slots, links, triggers, List.of());
        }
    }

    public record Output(int slot, MagicCircuit.Cast cast) {}

    private final UUID identity;
    private final AssemblyRules rules;
    private final NavigableMap<Integer, TickCircuit> slots = new TreeMap<>();
    private CircuitLinks links;
    private final NavigableMap<Integer, PlateLayers> overlays = new TreeMap<>();
    private final NavigableMap<Integer, TriggerBinding> triggers = new TreeMap<>();
    private final NavigableMap<Integer, BoardCondition> boards = new TreeMap<>();
    private BoardSafety boardPolicy;
    private int selected = 1, nextSlot = 2;

    public CircuitAssembly(UUID identity, AssemblyRules rules, TickCircuit first) {
        this.identity = Objects.requireNonNull(identity);
        this.rules = rules;
        slots.put(1, first);
        links = new CircuitLinks(rules);
    }

    public static CircuitAssembly restore(Snapshot s) {
        if (s.version < 1
                || s.version > 4
                || s.version == 1 && !s.triggers.isEmpty()
                || s.version < 3 && !s.overlays.isEmpty()
                || s.identity == null
                || s.slots.isEmpty()
                || s.slots.size() > s.rules.maxSlots()
                || s.nextSlot < 2
                || s.nextSlot > 65535)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._circuit_assembly.001"));
        CircuitAssembly a =
                new CircuitAssembly(
                        s.identity, s.rules, TickCircuit.restore(s.slots.get(0).circuit));
        a.slots.clear();
        for (Slot slot : s.slots)
            if (slot.id < 1
                    || slot.id >= s.nextSlot
                    || a.slots.put(slot.id, TickCircuit.restore(slot.circuit)) != null)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._circuit_assembly.002"));
        for (Slot slot : s.slots) {
            if (slot.board != null) {
                if (slot.board.supplyCursor() > a.circuit(slot.id).supplied())
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.domain.circuit._circuit_assembly.003"));
                a.boards.put(slot.id, slot.board);
                a.circuit(slot.id).blockInlet(slot.board.broken());
            }
        }
        if (!a.slots.containsKey(s.selected))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._circuit_assembly.004"));
        a.selected = s.selected;
        a.nextSlot = s.nextSlot;
        a.links = CircuitLinks.restore(s.links);
        if (!a.links.snapshot().rules().equals(s.rules))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._circuit_assembly.005"));
        for (var link : a.links.connections()) {
            if (!link.from().carrier().equals(s.identity)
                    || !link.to().carrier().equals(s.identity))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._circuit_assembly.006"));
            var from = a.circuit(link.from().slot()).definition().rune(link.from().rune()).kind();
            var to = a.circuit(link.to().slot()).definition().rune(link.to().rune()).kind();
            if (from
                            != (link.payload() == Route.Payload.MANA
                                    ? Rune.Kind.MANA_SEND
                                    : Rune.Kind.SIGNAL_SEND)
                    || to
                            != (link.payload() == Route.Payload.MANA
                                    ? Rune.Kind.MANA_RECEIVE
                                    : Rune.Kind.SIGNAL_RECEIVE))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._circuit_assembly.007"));
        }
        for (var t : s.triggers) {
            if (a.triggers.put(t.source(), t) != null)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._circuit_assembly.008"));
            a.circuit(t.source());
            requireInput(a.circuit(t.target()).definition());
        }
        for (var o : s.overlays)
            if (a.overlays.put(
                            o.slot(), new PlateLayers(a.circuit(o.slot()).definition(), o.state()))
                    != null)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._circuit_assembly.009"));
        return a;
    }

    /** Cheap immutable persistence stamp; never traverses rune arrays or builds a VM snapshot. */
    public record ChangeStamp(
            int selected,
            int nextSlot,
            List<Object> circuits,
            Object links,
            List<TriggerBinding> triggers,
            List<Overlay> overlays,
            Map<Integer, BoardCondition> boards) {
        public ChangeStamp {
            circuits = List.copyOf(circuits);
        }
    }

    public ChangeStamp changeStamp() {
        return new ChangeStamp(
                selected,
                nextSlot,
                slots.values().stream().map(TickCircuit::changeToken).toList(),
                links.changeToken(),
                triggers(),
                overlayStates(),
                Map.copyOf(boards));
    }

    public UUID identity() {
        return identity;
    }

    public AssemblyRules rules() {
        return rules;
    }

    public int selected() {
        return selected;
    }

    public List<Integer> slots() {
        return List.copyOf(slots.keySet());
    }

    public TickCircuit find(int id) {
        return slots.get(id);
    }

    public TickCircuit circuit(int id) {
        TickCircuit c = find(id);
        if (c == null)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._circuit_assembly.010"));
        return c;
    }

    public CircuitLinks links() {
        return links;
    }

    public void select(int id) {
        circuit(id);
        selected = id;
    }

    public int add(Rules rules) {
        requireInstallationPolicy();
        if (slots.size() >= this.rules.maxSlots() || nextSlot >= 65535)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._circuit_assembly.011"));
        int id = nextSlot++;
        slots.put(id, new TickCircuit(Blueprint.empty(rules)));
        if (boardPolicy != null) boards.put(id, boardPolicy.fresh(rules.material(), 0));
        selected = id;
        return id;
    }

    public void replace(int id, Blueprint definition) {
        if (circuit(id).definition().rules().material() != definition.rules().material())
            requireInstallationPolicy();
        if (triggers.values().stream().anyMatch(t -> t.target() == id)) requireInput(definition);
        if (links.references(identity, id))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._circuit_assembly.012"));
        var updated = layers(id).withRoot(definition);
        var runtime = circuit(id).reconfigure(definition);
        if (circuit(id).definition().rules().material() != definition.rules().material()) {
            boards.remove(id);
            if (boardPolicy != null)
                boards.put(
                        id, boardPolicy.fresh(definition.rules().material(), runtime.supplied()));
        }
        slots.put(id, runtime);
        runtime.blockInlet(boardBroken(id));
        storeLayers(id, updated);
    }

    public PlateLayers layers(int slot) {
        var root = circuit(slot).definition();
        var layers = overlays.get(slot);
        return layers == null ? new PlateLayers(root) : layers;
    }

    private List<Overlay> overlayStates() {
        return overlays.entrySet().stream()
                .map(e -> new Overlay(e.getKey(), e.getValue().state()))
                .toList();
    }

    private void storeLayers(int slot, PlateLayers layers) {
        if (layers.empty()) overlays.remove(slot);
        else overlays.put(slot, layers);
    }

    private void stopped() {
        if (powered())
            throw new IllegalStateException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._circuit_assembly.013"));
    }

    public int addLayer(int slot, int limit) {
        stopped();
        var next = layers(slot).add(limit);
        storeLayers(slot, next);
        return next.state().nextId() - 1;
    }

    public void replaceLayer(int slot, int layer, Blueprint definition) {
        if (layer == 1) {
            replace(slot, definition);
            return;
        }
        stopped();
        storeLayers(slot, layers(slot).replace(layer, definition));
    }

    public void configureLayer(int slot, int source, TriggerBinding binding) {
        stopped();
        storeLayers(slot, layers(slot).configure(source, binding));
    }

    public void removeLayer(int slot, int layer) {
        stopped();
        storeLayers(slot, layers(slot).remove(layer));
    }

    public List<TriggerBinding> triggers() {
        return List.copyOf(triggers.values());
    }

    public TriggerBinding trigger(int slot) {
        circuit(slot);
        return triggers.get(slot);
    }

    public void configureTrigger(int source, TriggerBinding binding) {
        circuit(source);
        if (powered())
            throw new IllegalStateException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._circuit_assembly.014"));
        if (binding == null) {
            triggers.remove(source);
            return;
        }
        if (binding.source() != source)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._circuit_assembly.015"));
        requireInput(circuit(binding.target()).definition());
        triggers.put(source, binding);
    }

    private static void requireInput(Blueprint definition) {
        if (definition.runes().stream().noneMatch(r -> r.kind() == Rune.Kind.INPUT))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._circuit_assembly.016"));
    }

    public void retimeMana(int steps) {
        slots.replaceAll((id, c) -> c.withManaSteps(steps));
        overlays.replaceAll(
                (id, p) ->
                        p.definition(1) == circuit(id).definition()
                                ? p
                                : p.withRoot(circuit(id).definition()));
    }

    public void reformCasting(CastingRules policy) {
        if (slots.values().stream().allMatch(c -> c.definition().rules().casting().equals(policy)))
            return;
        slots.replaceAll((id, c) -> c.withCasting(policy));
        overlays.replaceAll((id, p) -> p.withRoot(circuit(id).definition()));
    }

    public boolean active() {
        return slots.values().stream().anyMatch(TickCircuit::needsStep);
    }

    public boolean powered() {
        return slots.values().stream().anyMatch(TickCircuit::powered);
    }

    public void stopAll() {
        slots.values().forEach(TickCircuit::stop);
    }

    public void clearDrives() {
        slots.values().forEach(TickCircuit::clearDrives);
    }

    public void deliver() {
        links.deliver(id -> id.equals(identity) ? this : null);
    }

    public List<Output> advance(ManaSource source, int steps) {
        return advance(source, steps, ReleasePermit.UNLIMITED);
    }

    public List<Output> advance(ManaSource source, int steps, ReleasePermit release) {
        return advance(source, steps, release, ManaSource.NONE);
    }

    public List<Output> advance(
            ManaSource source, int steps, ReleasePermit release, ManaSource ambient) {
        return advance(
                source, steps, release, ambient, CastContext.NEUTRAL, ShapeRules.protocolV1());
    }

    public List<Output> advance(
            ManaSource source,
            int steps,
            ReleasePermit release,
            ManaSource ambient,
            CastContext context,
            ShapeRules shapes) {
        return advance(
                source, steps, release, ambient, context, shapes, slot -> CircuitProtection.NONE);
    }

    public List<Output> advance(
            ManaSource source,
            int steps,
            ReleasePermit release,
            ManaSource ambient,
            CastContext context,
            ShapeRules shapes,
            java.util.function.IntFunction<CircuitProtection> protection) {
        if (steps < 0 || steps > 2)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._circuit_assembly.017"));
        Objects.requireNonNull(release);
        List<Output> output = new ArrayList<>();
        for (int step = 0; step < steps; step++)
            for (var entry : slots.entrySet())
                if (!boardBroken(entry.getKey()) && entry.getValue().needsStep())
                    for (var cast :
                            entry.getValue()
                                    .tick(
                                            source,
                                            release,
                                            ambient,
                                            context,
                                            shapes,
                                            protection.apply(entry.getKey())))
                        output.add(new Output(entry.getKey(), cast));
        return List.copyOf(output);
    }

    public void publish() {
        links.publish(id -> id.equals(identity) ? this : null);
    }

    public List<Output> tick(ManaSource source, int steps) {
        clearDrives();
        deliver();
        var result = advance(source, steps);
        publish();
        return result;
    }

    public Snapshot snapshot() {
        return new Snapshot(
                !boards.isEmpty() ? 4 : !overlays.isEmpty() ? 3 : triggers.isEmpty() ? 1 : 2,
                identity,
                rules,
                selected,
                nextSlot,
                slots.entrySet().stream()
                        .map(
                                e ->
                                        new Slot(
                                                e.getKey(),
                                                e.getValue().snapshot(),
                                                boards.get(e.getKey())))
                        .toList(),
                links.snapshot(),
                triggers(),
                overlayStates());
    }

    public BoardCondition board(int slot) {
        circuit(slot);
        return boards.get(slot);
    }

    public boolean boardBroken(int slot) {
        return board(slot) != null && board(slot).broken();
    }

    public void prepareBoards(BoardSafety rules) {
        boardPolicy = rules;
        for (int slot : slots()) {
            boards.computeIfAbsent(
                    slot,
                    k ->
                            rules.fresh(
                                    circuit(k).definition().rules().material(),
                                    circuit(k).supplied()));
            circuit(slot).blockInlet(boardBroken(slot));
            if (boardBroken(slot) && circuit(slot).manaLoad() > 0) circuit(slot).quench();
            if (boardBroken(slot)) circuit(slot).stop();
        }
    }

    public BoardSafety.Result assessBoard(int slot, BoardSafety rules, boolean creative) {
        return assessBoard(slot, rules, creative, 1);
    }

    public BoardSafety.Result assessBoard(
            int slot, BoardSafety rules, boolean creative, double wearMultiplier) {
        var c = circuit(slot);
        var before = Objects.requireNonNull(board(slot));
        var result =
                rules.assess(
                        before,
                        c.definition().rules().material(),
                        c.manaLoad(),
                        c.supplied(),
                        creative,
                        wearMultiplier);
        boards.put(slot, result.condition());
        if (result.tripped()) {
            c.quench();
            c.blockInlet(true);
        }
        return result;
    }

    /** Repairs physical wear only. Melted boards require material replacement at a workbench. */
    public long repairBoard(int slot, long offered) {
        var before = board(slot);
        if (offered < 0)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._circuit_assembly.018"));
        if (before == null || before.melted()) return 0;
        long repaired = Math.min(offered, before.spent());
        if (repaired == 0) return 0;
        boards.put(
                slot,
                new BoardCondition(
                        1,
                        before.maximum(),
                        before.spent() - repaired,
                        false,
                        before.supplyCursor()));
        circuit(slot).blockInlet(false);
        return repaired;
    }

    public void renewBoard(int slot, BoardSafety rules) {
        if (circuit(slot).powered())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._circuit_assembly.019"));
        prepareBoards(rules);
        boards.put(
                slot,
                rules.fresh(
                        circuit(slot).definition().rules().material(), circuit(slot).supplied()));
        circuit(slot).blockInlet(false);
    }

    private void requireInstallationPolicy() {
        if (!boards.isEmpty() && boardPolicy == null)
            throw new IllegalStateException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._circuit_assembly.020"));
    }
}
