package dev.elementalcircuits.domain.circuit;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.casting.CastBudget;
import dev.elementalcircuits.domain.casting.CastContext;
import dev.elementalcircuits.domain.casting.CastingLedger;
import dev.elementalcircuits.domain.casting.CastingRules;
import dev.elementalcircuits.domain.casting.ReleasePermit;
import dev.elementalcircuits.domain.casting.ShapeRules;
import dev.elementalcircuits.domain.casting.ShapeVolume;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.logic.RuneLogic;
import dev.elementalcircuits.domain.logic.RuneLogicState;
import dev.elementalcircuits.domain.mana.ManaSource;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.domain.mana.Packet;
import dev.elementalcircuits.domain.mana.RefundTicket;

import java.util.*;

/** Synchronous control ticks with bounded, causally ordered mana transport phases. */
public final class TickCircuit extends MagicCircuit {
    private final Blueprint blueprint;
    private final List<Rune> nodes;
    private final Map<Integer, Integer> nodeIndices;
    private final boolean equipmentInput, fieldInput;
    private final int[] targetIndex;
    private final int[][] incoming,
            outgoing,
            manaIncoming,
            manaOutgoing,
            signalIncoming,
            signalOutgoing,
            splitWeights;
    private final boolean[] manaEdges;
    private Packet[] local, inbox, transit, pending;
    private boolean[] signals, gates;
    private Packet[] ports;
    private boolean[] drives, emitted, routeValidity, driveValidity, emittedValidity;
    private RuneLogicState[] logic;
    private long clock, sequence, supplied, loss, delivered, arrivalSequence;
    private boolean powered;
    private SupplyMode supplyMode = SupplyMode.CONFIGURED;
    private long charge = Fixed.ONE;

    public long charge() {
        return charge;
    }

    public CastContext invocationContext(CastContext base) {
        return base.charged(charge);
    }

    public SupplyMode supplyMode() {
        return supplyMode;
    }

    private boolean continuousSupply() {
        return supplyMode.continuous(blueprint);
    }

    private int snapshotVersion() {
        return 14;
    }

    private final CircuitComplexity complexity;
    private long[] wakeTicks;
    private long[] throttleRemaining;
    private CastingLedger castingLedger = CastingLedger.EMPTY;
    private CastContext castContext = CastContext.NEUTRAL;
    private ShapeRules shapeRules = ShapeRules.protocolV1();

    // Derived scratch only; no ownership or saved state depends on it.
    private boolean phaseMoved;
    private Mixture reserve = Mixture.EMPTY;
    private int remaining, injectionCountdown;
    private Object changeToken = new Object();

    public Object changeToken() {
        return changeToken;
    }

    private void changed() {
        changeToken = new Object();
    }

    public TickCircuit(Blueprint blueprint) {
        this.blueprint = Objects.requireNonNull(blueprint);
        complexity = CircuitComplexity.of(blueprint);
        nodes = blueprint.runes().stream().sorted(Comparator.comparingInt(Rune::id)).toList();
        equipmentInput = nodes.stream().anyMatch(r -> r.kind() == Rune.Kind.INPUT);
        fieldInput = nodes.stream().anyMatch(r -> r.kind() == Rune.Kind.ABSORB);
        int n = nodes.size(), e = blueprint.routes().size();
        targetIndex = new int[e];
        wakeTicks = new long[n];
        throttleRemaining = new long[n];
        logic = initialLogic(blueprint).toArray(RuneLogicState[]::new);
        local = new Packet[n];
        inbox = new Packet[e];
        transit = new Packet[e];
        pending = new Packet[e];
        signals = new boolean[e];
        gates = new boolean[n];
        incoming = new int[n][];
        outgoing = new int[n][];
        manaIncoming = new int[n][];
        signalIncoming = new int[n][];
        signalOutgoing = new int[n][];
        manaOutgoing = new int[n][];
        splitWeights = new int[n][];
        manaEdges = new boolean[e];
        ports = new Packet[n];
        drives = new boolean[n];
        emitted = new boolean[n];
        driveValidity = new boolean[n];
        emittedValidity = new boolean[n];
        routeValidity = new boolean[e];
        Arrays.fill(routeValidity, true);
        Map<Integer, Integer> index = new HashMap<>();
        List<List<Integer>> in = new ArrayList<>(), out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            index.put(nodes.get(i).id(), i);
            in.add(new ArrayList<>());
            out.add(new ArrayList<>());
        }
        nodeIndices = Map.copyOf(index);
        for (int edge = 0; edge < e; edge++) {
            Route route = blueprint.routes().get(edge);
            int from = index.get(route.source()), to = index.get(route.target());
            targetIndex[edge] = to;
            out.get(from).add(edge);
            in.get(to).add(edge);
            manaEdges[edge] = route.payload() == Route.Payload.MANA;
            if (nodes.get(from).kind() == Rune.Kind.SIGNAL_RECEIVE
                    || nodes.get(from).kind() == Rune.Kind.REDSTONE
                    || nodes.get(from).kind() == Rune.Kind.PROBE) routeValidity[edge] = false;
        }
        for (int i = 0; i < n; i++) {
            incoming[i] = in.get(i).stream().mapToInt(Integer::intValue).toArray();
            outgoing[i] = out.get(i).stream().mapToInt(Integer::intValue).toArray();
            manaIncoming[i] =
                    in.get(i).stream()
                            .filter(edge -> manaEdges[edge])
                            .mapToInt(Integer::intValue)
                            .toArray();
            signalIncoming[i] =
                    in.get(i).stream()
                            .filter(edge -> !manaEdges[edge])
                            .mapToInt(Integer::intValue)
                            .toArray();
            signalOutgoing[i] =
                    out.get(i).stream()
                            .filter(edge -> !manaEdges[edge])
                            .mapToInt(Integer::intValue)
                            .toArray();
            manaOutgoing[i] =
                    out.get(i).stream()
                            .filter(edge -> manaEdges[edge])
                            .mapToInt(Integer::intValue)
                            .toArray();
            splitWeights[i] = new int[manaOutgoing[i].length];
            Arrays.fill(splitWeights[i], 1);
            Rune rune = nodes.get(i);
            gates[i] =
                    rune.on()
                            && rune.kind() != Rune.Kind.SELECT
                            && rune.kind() != Rune.Kind.CONTACT;
            if (rune.kind() == Rune.Kind.SPLIT)
                for (int j = 0; j < splitWeights[i].length && j < rune.weights().size(); j++)
                    splitWeights[i][j] = rune.weights().get(j);
        }
    }

    private static List<RuneLogicState> initialLogic(Blueprint b) {
        return b.runes().stream()
                .sorted(Comparator.comparingInt(Rune::id))
                .map(RuneLogicState::initial)
                .toList();
    }

    public static TickCircuit restore(CircuitSnapshot s) {
        if (s == null || s.version() != 14 || s.blueprint() == null)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._tick_circuit.001"));
        if (s.arrivalSequence() < 0 || s.arrivalSequence() == Long.MAX_VALUE)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._tick_circuit.002"));
        TickCircuit c = new TickCircuit(s.blueprint());
        c.arrivalSequence = s.arrivalSequence();
        int n = c.nodes.size(), e = c.inbox.length;
        Packet[] savedLocal = s.local();
        Packet[] savedInbox = s.inbox();
        Packet[] savedTransit = s.transit();
        Packet[] savedPending = s.pending();
        boolean[] savedSignals = s.signals();
        boolean[] savedGates = s.gates();
        Packet[] savedPorts = s.ports();
        boolean[] savedDrives = s.drives();
        boolean[] savedEmitted = s.emitted();
        boolean[] savedRouteValidity = s.routeValidity();
        boolean[] savedDriveValidity = s.driveValidity();
        boolean[] savedEmittedValidity = s.emittedValidity();
        if (savedLocal.length != n
                || savedInbox.length != e
                || savedTransit.length != e
                || savedPending.length != e
                || savedSignals.length != e
                || savedGates.length != n
                || s.clock() < 0
                || s.sequence() < 0
                || s.supplied() < 0
                || s.loss() < 0
                || s.delivered() < 0
                || s.remaining() < 0
                || s.remaining() > c.blueprint.rules().window()
                || s.injectionCountdown() < -1
                || s.injectionCountdown() > c.blueprint.rules().interval())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._tick_circuit.003"));
        c.supplyMode = s.supplyMode();
        c.charge = s.charge();
        if (c.charge < Fixed.ONE || c.charge > 2 * Fixed.ONE)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._tick_circuit.004"));
        if (s.powered()
                        && (s.remaining() == 0
                                || c.nodes.stream()
                                        .noneMatch(
                                                r ->
                                                        r.kind() == Rune.Kind.INPUT
                                                                || r.kind() == Rune.Kind.ABSORB
                                                                || r.kind()
                                                                        == Rune.Kind.MANA_RECEIVE
                                                                || r.kind().signalOut()))
                || c.continuousSupply() && s.injectionCountdown() < 0
                || !c.continuousSupply() && s.injectionCountdown() > 0)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._tick_circuit.005"));
        if (s.reserve() == null || s.reserve().total() > c.packetCapacity())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._tick_circuit.006"));
        if (savedPorts.length != n
                || savedDrives.length != n
                || savedEmitted.length != n
                || savedRouteValidity.length != e
                || savedDriveValidity.length != n
                || savedEmittedValidity.length != n)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._tick_circuit.007"));
        if (s.logic().size() != n)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._tick_circuit.008"));
        for (int i = 0; i < n; i++) {
            s.logic().get(i).validate(c.nodes.get(i));
            if (s.logic().get(i).elapsed() > s.clock())
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._tick_circuit.009"));
        }
        c.castingLedger = s.castingLedger();
        long held = s.reserve().total();
        for (var ticket : c.castingLedger.pending()) {
            if (ticket.sequence() >= s.sequence()
                    || ticket.mana().latestArrival() > s.arrivalSequence()
                    || ticket.mana().order().stream().anyMatch(p -> !p.origin().refundable()))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._tick_circuit.010"));
            held = Math.addExact(held, ticket.mana().total());
        }
        if (s.reserve().latestArrival() > s.arrivalSequence())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._tick_circuit.011"));
        for (Packet[] array :
                new Packet[][] {savedLocal, savedInbox, savedTransit, savedPending, savedPorts})
            for (Packet p : array)
                if (p != null) {
                    if (p.mana().latestArrival() > s.arrivalSequence())
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.domain.circuit._tick_circuit.012"));
                    if (p.mana().total() > c.packetCapacity())
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.domain.circuit._tick_circuit.013"));
                    held = Math.addExact(held, p.mana().total());
                }
        for (int i = 0; i < n; i++) {
            Rune.Kind kind = c.nodes.get(i).kind();
            if (savedLocal[i] != null
                            && kind != Rune.Kind.STORAGE
                            && kind != Rune.Kind.INPUT
                            && kind != Rune.Kind.ABSORB
                            && kind != Rune.Kind.MANA_RECEIVE
                    || savedPorts[i] != null && kind != Rune.Kind.MANA_SEND
                    || savedDrives[i]
                            && kind != Rune.Kind.SIGNAL_RECEIVE
                            && kind != Rune.Kind.REDSTONE
                            && kind != Rune.Kind.PROBE
                    || savedEmitted[i] && kind != Rune.Kind.SIGNAL_SEND
                    || savedDriveValidity[i]
                            && kind != Rune.Kind.SIGNAL_RECEIVE
                            && kind != Rune.Kind.REDSTONE
                            && kind != Rune.Kind.PROBE
                    || savedEmittedValidity[i] && kind != Rune.Kind.SIGNAL_SEND)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._tick_circuit.014"));
        }
        for (int i = 0; i < e; i++)
            if (c.blueprint.routes().get(i).payload() == Route.Payload.SIGNAL
                    && (savedInbox[i] != null
                            || savedTransit[i] != null
                            || savedPending[i] != null))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._tick_circuit.015"));
        if (Math.addExact(
                        Math.addExact(held, s.loss()),
                        Math.addExact(s.delivered(), s.castingLedger().refunded()))
                != s.supplied())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._tick_circuit.016"));
        if (s.wakeTicks().length != n)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._tick_circuit.017"));
        c.wakeTicks = s.wakeTicks();
        for (int i = 0; i < n; i++) {
            long wake = c.wakeTicks[i];
            var kind = c.nodes.get(i).kind();
            if (wake < 0
                    || wake > Math.min(Long.MAX_VALUE - 1201, s.clock()) + 1200
                    || kind == Rune.Kind.STORAGE && wake > s.clock() + 1
                    || wake > 0 && (kind != Rune.Kind.OUTPUT && kind != Rune.Kind.STORAGE))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._tick_circuit.018"));
            if (kind == Rune.Kind.OUTPUT
                    && wake > 0
                    && Arrays.stream(c.manaIncoming[i]).noneMatch(edge -> savedInbox[edge] != null))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._tick_circuit.019"));
            if (kind == Rune.Kind.STORAGE
                    && savedLocal[i] != null
                    && savedLocal[i].mana().total() > c.nodes.get(i).storage().capacity())
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._tick_circuit.020"));
        }
        c.local = savedLocal;
        c.inbox = savedInbox;
        c.transit = savedTransit;
        c.pending = savedPending;
        c.signals = savedSignals;
        c.gates = savedGates;
        c.clock = s.clock();
        c.sequence = s.sequence();
        c.powered = s.powered();
        c.remaining = s.remaining();
        c.injectionCountdown = s.injectionCountdown();
        c.supplied = s.supplied();
        c.loss = s.loss();
        c.delivered = s.delivered();
        c.reserve = s.reserve();
        c.logic = s.logic().toArray(RuneLogicState[]::new);
        c.ports = savedPorts;
        c.drives = savedDrives;
        c.emitted = savedEmitted;
        c.routeValidity = savedRouteValidity;
        c.driveValidity = savedDriveValidity;
        c.emittedValidity = savedEmittedValidity;
        return c;
    }

    @Override
    public Blueprint definition() {
        return blueprint;
    }

    @Override
    public boolean powered() {
        return powered;
    }

    @Override
    public boolean start() {
        return start(SupplyMode.CONFIGURED);
    }

    public boolean start(SupplyMode mode) {
        return start(mode, Fixed.ONE);
    }

    public boolean start(SupplyMode mode, long charge) {
        if (inletBlocked) return false;
        if (charge < Fixed.ONE || charge > 2 * Fixed.ONE)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._tick_circuit.021"));
        if (powered
                || nodes.stream()
                        .noneMatch(
                                r ->
                                        r.kind() == Rune.Kind.INPUT
                                                || r.kind() == Rune.Kind.ABSORB
                                                || r.kind() == Rune.Kind.MANA_RECEIVE
                                                || r.kind().signalOut())) return false;
        changed();
        supplyMode = Objects.requireNonNull(mode);
        this.charge = charge;
        suspendLogic();
        Arrays.fill(emittedValidity, false);
        clearDrives();
        powered = true;
        remaining = blueprint.rules().window();
        injectionCountdown = 0;
        return true;
    }

    private void suspendLogic() {
        for (int i = 0; i < logic.length; i++)
            if (RuneLogic.sequential(nodes.get(i).kind())) {
                var next = logic[i].suspended();
                if (!next.equals(logic[i])) {
                    changed();
                    logic[i] = next;
                }
            }
    }

    @Override
    public void stop() {
        if (powered) {
            changed();
            powered = false;
        }
        suspendLogic();
    }

    public RuneLogicState logic(int rune) {
        Integer i = nodeIndices.get(rune);
        if (i == null)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._tick_circuit.022"));
        return logic[i];
    }

    public void resetLogic(int rune) {
        for (int i = 0; i < nodes.size(); i++)
            if (nodes.get(i).id() == rune && RuneLogic.sequential(nodes.get(i).kind())) {
                Rune r = nodes.get(i);
                RuneLogicState state = RuneLogicState.initial(r);
                if (r.kind() != Rune.Kind.CLOCK) {
                    boolean high = false, valid = true, connected = false;
                    for (int e : incoming[i])
                        if (!manaEdge(e)) {
                            high |= signals[e];
                            valid &= routeValidity[e];
                            connected = true;
                        }
                    if (powered && valid && connected)
                        state = new RuneLogicState(0, 0, state.latched(), high, true);
                }
                if (!state.equals(logic[i])) {
                    changed();
                    logic[i] = state;
                }
                return;
            }
        throw new IllegalArgumentException(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._tick_circuit.023"));
    }

    public long held() {
        long total = reserve.total();
        for (var ticket : castingLedger.pending()) total += ticket.mana().total();
        for (Packet[] array : new Packet[][] {local, inbox, transit, pending, ports})
            for (Packet p : array) if (p != null) total += p.mana().total();
        return total;
    }

    /** Refund tickets are already owed to their sources and cannot power the board again. */
    public long manaLoad() {
        long total = reserve.total();
        for (Packet[] array : new Packet[][] {local, inbox, transit, pending, ports})
            for (Packet p : array) if (p != null) total += p.mana().total();
        return total;
    }

    public long supplied() {
        return supplied;
    }

    public Mixture loadSignature() {
        long[] amounts = reserve.values();
        for (Packet[] array : new Packet[][] {local, inbox, transit, pending, ports})
            for (Packet p : array)
                if (p != null) {
                    long[] values = p.mana().values();
                    for (int i = 0; i < values.length; i++)
                        amounts[i] = Math.addExact(amounts[i], values[i]);
                }
        long total = manaLoad(), budget = Math.min(total, Fixed.MAX), assigned = 0;
        if (total > Fixed.MAX) {
            for (int i = 0; i < amounts.length; i++) {
                amounts[i] =
                        java.math.BigInteger.valueOf(amounts[i])
                                .multiply(java.math.BigInteger.valueOf(budget))
                                .divide(java.math.BigInteger.valueOf(total))
                                .longValueExact();
                assigned += amounts[i];
            }
            for (int i = 0; assigned < budget; i = (i + 1) % amounts.length) {
                amounts[i]++;
                assigned++;
            }
        }
        return new Mixture(amounts);
    }

    private boolean inletBlocked;

    public void blockInlet(boolean blocked) {
        inletBlocked = blocked;
    }

    /** Safety trip dissipates owned mana once. Existing refund claims retain their provenance. */
    public void quench() {
        stop();
        loss = Math.addExact(loss, manaLoad());
        reserve = Mixture.EMPTY;
        for (Packet[] array : new Packet[][] {local, inbox, transit, pending, ports})
            Arrays.fill(array, null);
        Arrays.fill(signals, false);
        clearDrives();
        changed();
    }

    public enum Phase {
        RUNNING,
        DRAINING,
        QUIESCENT
    }

    /**
     * Stored reserve and offered ports cannot advance internally without new input or a manual
     * start.
     */
    public Phase phase() {
        if (powered) return Phase.RUNNING;
        if (blueprint.rules().casting().modern() && held() > 0) return Phase.DRAINING;
        for (Packet[] array : new Packet[][] {local, inbox, transit, pending})
            for (Packet packet : array) if (packet != null) return Phase.DRAINING;
        return Phase.QUIESCENT;
    }

    public boolean needsStep() {
        return phase() != Phase.QUIESCENT;
    }

    @Override
    public boolean editable() {
        return !powered && held() <= packetCapacity();
    }

    public void toggleManual(int id) {
        for (int i = 0; i < nodes.size(); i++)
            if (nodes.get(i).id() == id
                    && nodes.get(i).kind() == Rune.Kind.SWITCH
                    && !nodes.get(i).signalControl()) {
                changed();
                gates[i] = !gates[i];
            }
    }

    public String status() {
        return powered
                ? dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._tick_circuit.024")
                : held() > 0
                        ? dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._tick_circuit.025")
                        : dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._tick_circuit.026");
    }

    public TickCircuit reconfigure(Blueprint definition) {
        if (!editable())
            throw new IllegalStateException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._tick_circuit.027"));
        TickCircuit next = new TickCircuit(definition);
        Mixture recovered = reserve;
        for (Packet[] array : new Packet[][] {local, inbox, transit, pending, ports})
            for (Packet p : array) if (p != null) recovered = recovered.mergeArrivals(p.mana());
        if (recovered.total()
                > (definition.rules().casting().explicitStorage()
                        ? Fixed.MAX
                        : definition.rules().nodeCapacity()))
            throw new IllegalStateException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._tick_circuit.028"));
        next.reserve = recovered;
        next.arrivalSequence = arrivalSequence;
        next.castingLedger = castingLedger;
        next.clock = clock;
        next.sequence = sequence;
        next.supplied = supplied;
        next.loss = loss;
        next.delivered = delivered;
        return next;
    }

    private boolean expandedShapes() {
        if (nodes.stream().anyMatch(r -> ShapeVolume.rune(r.kind()))) return true;
        for (var array : new Packet[][] {local, inbox, transit, pending, ports})
            for (var p : array) if (p != null && ShapeVolume.shaped(p.spell().shape())) return true;
        return false;
    }

    @Override
    public CircuitSnapshot snapshot() {
        return new CircuitSnapshot(
                snapshotVersion(),
                blueprint,
                clock,
                sequence,
                powered,
                remaining,
                injectionCountdown,
                supplied,
                loss,
                delivered,
                reserve,
                local,
                inbox,
                transit,
                pending,
                signals,
                gates,
                ports,
                drives,
                emitted,
                routeValidity,
                driveValidity,
                emittedValidity,
                Arrays.asList(logic),
                arrivalSequence,
                castingLedger,
                wakeTicks,
                supplyMode,
                charge);
    }

    private int node(int id, Rune.Kind kind) {
        Integer i = nodeIndices.get(id);
        if (i == null || nodes.get(i).kind() != kind)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._tick_circuit.029"));
        return i;
    }

    @Override
    public boolean receive(int rune, Packet packet) {
        if (inletBlocked) return false;
        int i = node(rune, Rune.Kind.MANA_RECEIVE);
        if (!powered
                || packet == null
                || arrivalSequence == Long.MAX_VALUE - 1
                || !fits(local[i], packet)
                || supplied > Long.MAX_VALUE - packet.mana().total()) return false;
        changed();
        Packet arriving = stamp(packet);
        local[i] = local[i] == null ? arriving : local[i].merge(arriving);
        supplied += packet.mana().total();
        return true;
    }

    @Override
    public Packet offered(int rune) {
        return ports[node(rune, Rune.Kind.MANA_SEND)];
    }

    @Override
    public void export(int rune, Packet expected) {
        int i = node(rune, Rune.Kind.MANA_SEND);
        if (expected == null
                || !expected.equals(ports[i])
                || delivered > Long.MAX_VALUE - expected.mana().total())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._tick_circuit.030"));
        changed();
        ports[i] = null;
        delivered += expected.mana().total();
    }

    @Override
    public void clearDrives() {
        for (int i = 0; i < drives.length; i++)
            if (drives[i] || driveValidity[i]) {
                changed();
                break;
            }
        Arrays.fill(drives, false);
        Arrays.fill(driveValidity, false);
    }

    @Override
    public void drive(int rune, boolean value) {
        Integer i = nodeIndices.get(rune);
        if (i == null
                || nodes.get(i).kind() != Rune.Kind.REDSTONE
                        && nodes.get(i).kind() != Rune.Kind.PROBE
                        && nodes.get(i).kind() != Rune.Kind.SIGNAL_RECEIVE)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._tick_circuit.031"));
        if (!driveValidity[i] || value && !drives[i]) changed();
        drives[i] |= value;
        driveValidity[i] = true;
    }

    @Override
    public boolean validSignal(int rune) {
        return powered && emittedValidity[node(rune, Rune.Kind.SIGNAL_SEND)];
    }

    @Override
    public boolean signal(int rune) {
        return validSignal(rune) && emitted[node(rune, Rune.Kind.SIGNAL_SEND)];
    }

    private boolean manaEdge(int e) {
        return manaEdges[e];
    }

    private Packet stamp(Packet packet) {
        return new Packet(packet.mana().arriving(++arrivalSequence), packet.spell());
    }

    private boolean fits(Packet a, Packet b) {
        if (b == null) return true;
        try {
            b = new Packet(b.mana().arriving(arrivalSequence + 1), b.spell());
            return (a == null ? b : a.merge(b)).mana().total() <= packetCapacity();
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    public List<Cast> tick(ManaSource source) {
        return tick(source, ReleasePermit.UNLIMITED);
    }

    public List<Cast> tick(ManaSource source, ReleasePermit release) {
        return tick(source, release, ManaSource.NONE);
    }

    public boolean singleInjectionSpent() {
        return !continuousSupply() && injectionCountdown < 0;
    }

    private boolean explicitStorage() {
        return blueprint.rules().casting().explicitStorage();
    }

    private long packetCapacity() {
        return explicitStorage() ? Fixed.MAX : blueprint.rules().nodeCapacity();
    }

    public CircuitComplexity complexity() {
        return complexity;
    }

    public int castingDelay(long speed) {
        return blueprint.rules().casting().standard().delay(complexity, speed);
    }

    private long intake(int i) {
        return explicitStorage()
                ? blueprint.inputAmount(nodes.get(i))
                : Math.min(blueprint.inputAmount(nodes.get(i)), blueprint.rules().nodeCapacity());
    }

    public List<Cast> tick(ManaSource source, ReleasePermit release, ManaSource ambient) {
        return tick(source, release, ambient, CastContext.NEUTRAL, ShapeRules.protocolV1());
    }

    public List<Cast> tick(
            ManaSource source,
            ReleasePermit release,
            ManaSource ambient,
            CastContext context,
            ShapeRules shapes) {
        return tick(source, release, ambient, context, shapes, CircuitProtection.NONE);
    }

    public List<Cast> tick(
            ManaSource source,
            ReleasePermit release,
            ManaSource ambient,
            CastContext context,
            ShapeRules shapes,
            CircuitProtection protection) {
        this.castContext = invocationContext(Objects.requireNonNull(context));
        this.shapeRules = Objects.requireNonNull(shapes);
        Objects.requireNonNull(release);
        Objects.requireNonNull(ambient);
        if (!needsStep()) return List.of();
        // Refuse exhausted counters before any ownership transfer or world effect.
        if (castingLedger.failures() > Long.MAX_VALUE - CircuitBounds.RUNES * 64L
                || arrivalSequence
                        > Long.MAX_VALUE - 2 - (CircuitBounds.RUNES + CircuitBounds.ROUTES * 128L)
                || clock > Long.MAX_VALUE - 1201
                || sequence > Long.MAX_VALUE - CircuitBounds.RUNES * 64L
                || supplied
                        > Long.MAX_VALUE
                                - Fixed.MAX
                                        * (CircuitBounds.ROUTES * 128L + CircuitBounds.RUNES * 128L)
                || loss
                        > Long.MAX_VALUE
                                - Fixed.MAX
                                        * (CircuitBounds.ROUTES * 128L + CircuitBounds.RUNES * 128L)
                || delivered
                        > Long.MAX_VALUE
                                - Fixed.MAX
                                        * (CircuitBounds.ROUTES * 128L
                                                + CircuitBounds.RUNES * 128L)) {
            stop();
            return List.of();
        }
        changed();
        clock++;
        for (int i = 0; i < nodes.size(); i++)
            throttleRemaining[i] =
                    nodes.get(i).kind() == Rune.Kind.THROTTLE ? nodes.get(i).value() : 0;
        if (blueprint.rules().casting().modern()) decay();
        deliverTransit();
        boolean due = powered && injectionCountdown == 0;
        long cost = 0, injectionTotal = 0;
        boolean canInject = true;
        if (powered) {
            Mixture quotedReserve = reserve;
            for (int i = 0; i < nodes.size(); i++) {
                Rune r = nodes.get(i);
                if (r.kind().signalOut()
                                && (r.kind() != Rune.Kind.STORAGE || signalOutgoing[i].length > 0)
                        || r.kind() == Rune.Kind.SIGNAL_SEND
                        || (r.kind() == Rune.Kind.CONTACT || r.kind() == Rune.Kind.SELECT)
                                && signalIncoming[i].length > 0
                        || (r.kind() == Rune.Kind.SWITCH || r.kind() == Rune.Kind.STORAGE)
                                && r.signalControl()) cost += blueprint.rules().signalCost();
                if (due && r.kind() == Rune.Kind.INPUT) {
                    long amount = intake(i), reused = Math.min(quotedReserve.total(), amount);
                    Mixture recycled = reused > 0 ? quotedReserve.take(reused) : Mixture.EMPTY;
                    if (amount > 0
                            && fits(
                                    local[i],
                                    new Packet(
                                            recycled.plus(source.preview(amount - reused)),
                                            SpellSpec.EMPTY))) {
                        injectionTotal += amount;
                        quotedReserve = quotedReserve.minus(recycled);
                    } else canInject = false;
                }
            }
            // Capacity backpressure stalls the entire due injection, never drains unusable supply.
            if (!canInject) {
                injectionTotal = 0;
            }
            long externalInjection = Math.max(0, injectionTotal - reserve.total());
            cost += externalInjection;
            // A field-only circuit maintains its control from the same field; mixed circuits use
            // equipment.
            ManaSource controlSource = equipmentInput ? source : fieldInput ? ambient : source;
            Mixture withdrawn = cost > Fixed.MAX ? null : controlSource.draw(cost);
            if (withdrawn == null) {
                stop();
                if (blueprint.rules().casting().modern()) {
                    if (explicitStorage())
                        castingLedger =
                                new CastingLedger(
                                        castingLedger.refunded(),
                                        castingLedger.failures() + 1,
                                        castingLedger.pending());
                    else failOwned();
                    return List.of();
                }
            } else {
                supplied += cost;
                Mixture fresh = withdrawn.take(externalInjection);
                if (due && canInject) {
                    for (int i = 0; i < nodes.size(); i++)
                        if (nodes.get(i).kind() == Rune.Kind.INPUT) {
                            long amount = intake(i);
                            if (amount == 0) continue;
                            long reused = Math.min(reserve.total(), amount);
                            Mixture m = Mixture.EMPTY;
                            if (reused > 0) {
                                if (reused == reserve.total()) {
                                    m = reserve;
                                    reserve = Mixture.EMPTY;
                                } else {
                                    List<Mixture> parts = proportionalTake(reserve, reused);
                                    m = parts.get(0);
                                    reserve = parts.get(1);
                                }
                            }
                            Mixture added = fresh.take(amount - reused);
                            fresh = fresh.minus(added);
                            m = m.plus(added);
                            Packet p = stamp(new Packet(m, SpellSpec.EMPTY));
                            local[i] = local[i] == null ? p : local[i].merge(p);
                        }
                    injectionCountdown = continuousSupply() ? blueprint.rules().interval() : -1;
                }
                loss += cost - externalInjection;
            }
        }
        // Harvest only while explicitly powered. Refused/blocked outputs retain their owned
        // resource.
        if (powered && (explicitStorage() ? due : continuousSupply() || due))
            for (int i = 0; i < nodes.size(); i++)
                if (nodes.get(i).kind() == Rune.Kind.ABSORB) {
                    long held = local[i] == null ? 0 : local[i].mana().total();
                    long desired =
                            Math.min(blueprint.inputAmount(nodes.get(i)), packetCapacity() - held);
                    long reused = Math.min(desired, reserve.total()),
                            amount = Math.min(desired - reused, ambient.available());
                    if (reused > 0 || amount > 0) {
                        Mixture quote =
                                (reused > 0 ? reserve.take(reused) : Mixture.EMPTY)
                                        .plus(ambient.preview(amount));
                        if (!fits(local[i], new Packet(quote, SpellSpec.EMPTY))
                                || !ambient.debit(amount)) continue;
                        Mixture mana = quote;
                        if (reused > 0) reserve = reserve.minus(reserve.take(reused));
                        Packet p = stamp(new Packet(mana, SpellSpec.EMPTY));
                        local[i] = local[i] == null ? p : local[i].merge(p);
                        supplied += amount;
                    }
                    if (injectionCountdown == 0)
                        injectionCountdown = continuousSupply() ? blueprint.rules().interval() : -1;
                }
        if (!protection.admit(this)) return List.of();
        boolean[] next = new boolean[signals.length], nextValidity = new boolean[signals.length];
        List<Cast> casts = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) sampleControl(i, next, nextValidity);
        for (int round = 0; round < blueprint.rules().manaSteps(); round++) {
            phaseMoved = false;
            if (round > 0) deliverTransit();
            for (int i = 0; i < nodes.size(); i++) {
                transferMana(i, release, casts);
            }
            for (int e = 0; e < pending.length; e++)
                if (transit[e] == null && pending[e] != null) {
                    transit[e] = pending[e];
                    pending[e] = null;
                    phaseMoved = true;
                }
            if (!phaseMoved) break;
        }
        if (explicitStorage()) expireOrdinary();
        // Internal control state freezes on power loss; signal() separately gates external
        // validity.
        if (powered) {
            signals = next;
            routeValidity = nextValidity;
        }
        if (powered && !continuousSupply() && --remaining == 0) stop();
        if (powered && injectionCountdown > 0) injectionCountdown--;
        return List.copyOf(casts);
    }

    private void sampleControl(int i, boolean[] next, boolean[] nextValidity) {
        Rune rune = nodes.get(i);
        boolean any = false, all = true, hasSignal = false, valid = true;
        for (int e : signalIncoming[i]) {
            any |= signals[e];
            all &= signals[e];
            hasSignal = true;
            valid &= routeValidity[e];
        }
        if (powered) {
            if (rune.kind() == Rune.Kind.INTERACT
                    || rune.kind() == Rune.Kind.CONTACT
                    || rune.kind() == Rune.Kind.SELECT) gates[i] = hasSignal && valid && any;
            if ((rune.kind() == Rune.Kind.SWITCH || rune.kind() == Rune.Kind.STORAGE)
                    && rune.signalControl()
                    && valid) gates[i] = any;
            boolean value =
                    switch (rune.kind()) {
                        case SIGNAL -> rune.on();
                        case AND -> hasSignal && all;
                        case OR -> any;
                        case NOT -> !any;
                        case SENSOR -> sensor(i, rune);
                        default -> false;
                    };
            if (RuneLogic.sequential(rune.kind())) {
                var result = RuneLogic.step(rune, logic[i], any, valid, hasSignal);
                logic[i] = result.state();
                value = result.value();
                valid = result.valid();
            }
            if (rune.kind() == Rune.Kind.SIGNAL_RECEIVE
                    || rune.kind() == Rune.Kind.REDSTONE
                    || rune.kind() == Rune.Kind.PROBE) {
                value = drives[i];
                valid = driveValidity[i];
            }
            if (rune.kind() == Rune.Kind.SIGNAL_SEND) {
                emittedValidity[i] = hasSignal && valid;
                if (valid) emitted[i] = any;
            }
            for (int e : signalOutgoing[i]) {
                if (rune.kind() == Rune.Kind.STORAGE) {
                    long stored = local[i] == null ? 0 : local[i].mana().total();
                    value =
                            switch (blueprint.routes().get(e).sourcePort()) {
                                case 0 -> stored >= rune.storage().capacity();
                                case 1 -> stored >= rune.storage().threshold() && gates[i];
                                case 2 -> stored == 0;
                                default ->
                                        throw new IllegalStateException(
                                                dev.elementalcircuits.text.Texts.get(
                                                        "text.elementalcircuits.domain.circuit._tick_circuit.032"));
                            };
                    valid = true;
                }
                next[e] = value;
                nextValidity[e] = valid;
            }
        }
    }

    private void transferMana(int i, ReleasePermit release, List<Cast> casts) {
        Rune rune = nodes.get(i);
        if (rune.kind() == Rune.Kind.STORAGE) {
            storage(i);
            return;
        }
        // Already owned branch shares drain separately; new batches wait until all are allocated.
        boolean busy = false;
        for (int e : outgoing[i]) if (pending[e] != null) busy = true;
        if (busy
                || rune.kind() == Rune.Kind.SWITCH && !gates[i]
                || rune.kind() == Rune.Kind.CONTACT && gates[i]) return;
        Packet batch = local[i];
        boolean hasMana = false, complete = true;
        try {
            for (int e : manaIncoming[i]) {
                hasMana = true;
                complete &= inbox[e] != null;
                if (inbox[e] != null) batch = batch == null ? inbox[e] : batch.merge(inbox[e]);
            }
            if (batch == null
                    || rune.kind() == Rune.Kind.AND && hasMana && !complete
                    || batch.mana().total() > packetCapacity()) return;
            if (rune.kind() == Rune.Kind.THROTTLE) {
                throttle(i, batch);
                return;
            }
            if (rune.kind() == Rune.Kind.MANA_SEND) {
                if (ports[i] != null) return;
                consume(i);
                ports[i] = batch;
                return;
            }
            if (rune.kind() == Rune.Kind.OUTPUT) {
                if (!batch.spell().compatibleOutput()) return;
                if (batch.spell().method() == SpellSpec.Method.CONTACT
                        && contactReleased(batch.spell().connection().control())) return;
                if (explicitStorage()) {
                    if (wakeTicks[i] == 0) {
                        int delay = castingDelay(castContext.speed());
                        wakeTicks[i] = clock + delay;
                    }
                    if (clock < wakeTicks[i]) return;
                }
                if (blueprint.rules().casting().modern()) {
                    releaseModern(i, batch, release, casts);
                    return;
                }
                long request = batch.spell().amount();
                if (request > batch.mana().total()) return;
                // Amount is a payment request; unspent components remain at the same inputs.
                Mixture payment = batch.mana();
                Mixture remainder = Mixture.EMPTY;
                if (request > 0 && request < batch.mana().total()) {
                    List<Mixture> parts = proportionalTake(batch.mana(), request);
                    payment = parts.get(0);
                    remainder = parts.get(1);
                }
                long divisor = 1;
                if (batch.spell().range() > 0) divisor *= Math.max(1, batch.spell().range());
                if (batch.spell().radius() > 0) {
                    long d = 2L * batch.spell().radius() + 1;
                    divisor *= d * d * d;
                }
                if (batch.spell().aim() >= 0)
                    divisor *= 1 + (Fixed.ONE - batch.spell().aim()) * 4 / Fixed.ONE;
                Mixture effect = payment.divide(1, divisor);
                if (effect.empty()) return;
                // Pure mana has no world spell. Only a real elemental release uses the wand.
                if ((batch.spell().method() == SpellSpec.Method.CONTACT
                                || effect.total() > effect.values()[0])
                        && !release.tryAcquire()) return;
                consume(i);
                if (!remainder.empty()) {
                    int first = manaIncoming[i][0];
                    inbox[first] = new Packet(remainder, batch.spell());
                }
                loss += payment.total() - effect.total();
                delivered += effect.total();
                casts.add(new Cast(sequence++, effect, batch.spell(), rune.id()));
                return;
            }
            int[] edges = manaOutgoing[i];
            if (edges.length == 0) return;
            Mixture mana = batch.mana();
            if (rune.kind() == Rune.Kind.CONVERT)
                mana =
                        mana.convert(
                                rune.element(),
                                blueprint
                                        .rules()
                                        .casting()
                                        .retention(blueprint.rules().conversion()));
            if (rune.kind() == Rune.Kind.NOT)
                mana =
                        mana.inverse(
                                blueprint
                                        .rules()
                                        .casting()
                                        .retention(blueprint.rules().inversion()));
            if (rune.kind() == Rune.Kind.SPLIT || rune.kind() == Rune.Kind.MERGE) {
                mana =
                        mana.retain(
                                blueprint
                                        .rules()
                                        .casting()
                                        .retention(blueprint.rules().routingRetention()));
                if (rune.kind() == Rune.Kind.MERGE
                        && !blueprint.rules().casting().modern()
                        && mana.opposed())
                    mana = mana.retain(blueprint.rules().oppositionRetention());
            }
            SpellSpec spec = batch.spell().modify(rune);
            if (rune.kind() == Rune.Kind.INTERACT)
                spec = spec.withConnection(spec.connection().withSignal(gates[i]));
            if (rune.kind() == Rune.Kind.FILTER || rune.kind() == Rune.Kind.SELECT) {
                routeSelection(i, batch, spec);
                return;
            }
            List<Mixture> shares = mana.split(splitWeights[i]);
            consume(i);
            loss += batch.mana().total() - mana.total();
            for (int j = 0; j < edges.length; j++) {
                int e = edges[j];
                Mixture before = shares.get(j),
                        after = blueprint.routes().get(e).transmit(before, blueprint.rules());
                loss += before.total() - after.total();
                if (!after.empty()) pending[e] = new Packet(after, spec);
            }
        } catch (IllegalArgumentException conflict) {
            /* Keep original inputs on an incompatible batch. */
        }
    }

    private boolean sensor(int index, Rune rune) {
        long amount = inputTotal(index);
        if (rune.weights().isEmpty()) return amount >= Math.max(1, rune.value());
        if (gates[index]) {
            if (amount <= rune.weights().getFirst()) gates[index] = false;
        } else if (amount >= rune.value()) gates[index] = true;
        return gates[index];
    }

    private void throttle(int index, Packet batch) {
        Mixture accepted =
                batch.mana().takeOrdered(Math.min(throttleRemaining[index], batch.mana().total()));
        Mixture remainder = batch.mana().minus(accepted);
        Mixture[] groups = {accepted, remainder};
        int[][] edges = new int[2][];
        for (int port = 0; port < 2; port++) {
            final int selected = port;
            edges[port] =
                    Arrays.stream(manaOutgoing[index])
                            .filter(e -> blueprint.routes().get(e).sourcePort() == selected)
                            .toArray();
            if (!groups[port].empty() && edges[port].length == 0) return;
        }
        consume(index);
        throttleRemaining[index] -= accepted.total();
        for (int port = 0; port < 2; port++) {
            if (groups[port].empty()) continue;
            int[] weights = new int[edges[port].length];
            Arrays.fill(weights, 1);
            List<Mixture> shares = groups[port].split(weights);
            for (int j = 0; j < edges[port].length; j++) {
                int edge = edges[port][j];
                Mixture before = shares.get(j);
                Mixture after = blueprint.routes().get(edge).transmit(before, blueprint.rules());
                loss += before.total() - after.total();
                if (!after.empty()) pending[edge] = new Packet(after, batch.spell());
            }
        }
    }

    /** Selected branches are disjoint ownership partitions, then fan out within each port. */
    private void routeSelection(int i, Packet batch, SpellSpec spec) {
        Rune rune = nodes.get(i);
        Mixture selected;
        if (rune.kind() == Rune.Kind.FILTER) {
            int component = rune.element().component(!rune.on());
            selected =
                    Mixture.ordered(
                            batch.mana().order().stream()
                                    .filter(p -> p.component() == component)
                                    .toList());
        } else selected = gates[i] ? Mixture.EMPTY : batch.mana();
        Mixture[] groups = {selected, batch.mana().minus(selected)};
        int[][] edges = new int[2][];
        for (int port = 0; port < 2; port++) {
            final int p = port;
            edges[port] =
                    Arrays.stream(manaOutgoing[i])
                            .filter(e -> blueprint.routes().get(e).sourcePort() == p)
                            .toArray();
            if (!groups[port].empty() && edges[port].length == 0) return;
        }
        consume(i);
        for (int port = 0; port < 2; port++) {
            if (groups[port].empty()) continue;
            int[] weights = new int[edges[port].length];
            Arrays.fill(weights, 1);
            var shares = groups[port].split(weights);
            for (int j = 0; j < edges[port].length; j++) {
                int e = edges[port][j];
                var before = shares.get(j);
                var after = blueprint.routes().get(e).transmit(before, blueprint.rules());
                loss += before.total() - after.total();
                if (!after.empty()) pending[e] = new Packet(after, spec);
            }
        }
    }

    public boolean contactReleased(int rune) {
        Integer i = nodeIndices.get(rune);
        return powered && i != null && nodes.get(i).kind() == Rune.Kind.CONTACT && gates[i];
    }

    private void releaseModern(int i, Packet batch, ReleasePermit release, List<Cast> casts) {
        var quote = CastBudget.quote(batch.spell(), blueprint.rules(), castContext, shapeRules);
        if (quote.required() > batch.mana().total()) {
            if (castingLedger.pending().size() >= 1024) return;
            consume(i);
            fail(batch.mana());
            return;
        }
        Mixture paid = batch.mana().take(quote.required());
        Mixture effect = paid.take(quote.effect());
        Mixture remainder = batch.mana().minus(paid);
        if (!explicitStorage()
                && reserve.total() + remainder.total() > blueprint.rules().nodeCapacity()) return;
        if (explicitStorage() && !canReturn(remainder)) return;
        if ((batch.spell().method() == SpellSpec.Method.CONTACT
                        || effect.total() > effect.values()[0])
                && !release.tryAcquire()) return;
        consume(i);
        if (!remainder.empty()) {
            if (explicitStorage()) returnUnused(remainder);
            else reserve = reserve.mergeArrivals(remainder);
        }
        loss += paid.total() - effect.total();
        delivered += effect.total();
        casts.add(new Cast(sequence++, effect, quote.resolved(), nodes.get(i).id()));
    }

    public long failures() {
        return castingLedger.failures();
    }

    public long refunded() {
        return castingLedger.refunded();
    }

    public List<RefundTicket> refunds() {
        return castingLedger.pending();
    }

    public void acknowledgeRefund(long id) {
        var queue = new ArrayList<>(castingLedger.pending());
        var ticket = queue.stream().filter(t -> t.sequence() == id).findFirst().orElseThrow();
        queue.remove(ticket);
        castingLedger =
                new CastingLedger(
                        Math.addExact(castingLedger.refunded(), ticket.mana().total()),
                        castingLedger.failures(),
                        queue);
        changed();
    }

    private void fail(Mixture mana) {
        Mixture refund = blueprint.rules().casting().refundable(mana);
        var queue = new ArrayList<>(castingLedger.pending());
        if (!refund.empty()) queue.add(new RefundTicket(sequence++, refund));
        castingLedger =
                new CastingLedger(
                        castingLedger.refunded(),
                        Math.addExact(castingLedger.failures(), 1),
                        queue);
        loss += mana.total() - refund.total();
        changed();
    }

    private void failOwned() {
        long attempts = castingLedger.failures();
        if (!reserve.empty() && castingLedger.pending().size() < 1024) {
            Mixture m = reserve;
            reserve = Mixture.EMPTY;
            fail(m);
        }
        for (var group : new Packet[][] {local, inbox, transit, pending, ports})
            for (int i = 0; i < group.length; i++)
                if (group[i] != null && castingLedger.pending().size() < 1024) {
                    var m = group[i].mana();
                    group[i] = null;
                    fail(m);
                }
        castingLedger =
                new CastingLedger(
                        castingLedger.refunded(),
                        Math.addExact(attempts, 1),
                        castingLedger.pending());
        changed();
    }

    private Mixture decayed(Mixture mana, long rate) {
        if (mana.empty() || rate == 0) return mana;
        var after = mana.retain(Fixed.ONE - rate);
        loss += mana.total() - after.total();
        return after;
    }

    private void decay() {
        var policy = blueprint.rules().casting();
        if (explicitStorage() && !reserve.empty() && canReturn(reserve)) {
            returnUnused(reserve);
            reserve = Mixture.EMPTY;
        }
        reserve = decayed(reserve, policy.cacheLoss());
        for (var group : new Packet[][] {local, inbox, transit, pending, ports}) {
            long rate =
                    group == transit || group == pending ? policy.movingLoss() : policy.cacheLoss();
            for (int i = 0; i < group.length; i++)
                if (group[i] != null) {
                    var before = group[i];
                    long effective =
                            explicitStorage()
                                            && group == local
                                            && nodes.get(i).kind() == Rune.Kind.STORAGE
                                    ? policy.standard().storageLoss()
                                    : rate;
                    var after = decayed(before.mana(), effective);
                    group[i] = after.empty() ? null : new Packet(after, before.spell());
                }
        }
        if (explicitStorage())
            for (int i = 0; i < nodes.size(); i++)
                if (nodes.get(i).kind() == Rune.Kind.OUTPUT
                        && wakeTicks[i] > 0
                        && Arrays.stream(manaIncoming[i]).noneMatch(edge -> inbox[edge] != null))
                    wakeTicks[i] = 0;
    }

    private long inputTotal(int i) {
        long t = local[i] == null ? 0 : local[i].mana().total();
        for (int e : manaIncoming[i]) if (inbox[e] != null) t += inbox[e].mana().total();
        return t;
    }

    private void consume(int i) {
        phaseMoved = true;
        wakeTicks[i] = 0;
        local[i] = null;
        for (int e : manaIncoming[i]) inbox[e] = null;
    }

    private void deliverTransit() {
        for (int e = 0; e < transit.length; e++)
            if (transit[e] != null && !scheduledTarget(e) && fits(inbox[e], transit[e])) {
                Packet arriving = stamp(transit[e]);
                inbox[e] = inbox[e] == null ? arriving : inbox[e].merge(arriving);
                transit[e] = null;
                phaseMoved = true;
            }
    }

    private boolean scheduledTarget(int edge) {
        if (!explicitStorage()) return false;
        // The compiled target table below avoids a scan in large transport phases.
        return wakeTicks[targetIndex[edge]] > 0
                && nodes.get(targetIndex[edge]).kind() == Rune.Kind.OUTPUT;
    }

    private boolean canReturn(Mixture mana) {
        return mana.empty()
                || castingLedger.pending().size() < 1024
                || mana.order().stream().noneMatch(p -> p.origin().refundable());
    }

    private void returnUnused(Mixture mana) {
        if (mana.empty()) return;
        var parts = mana.order().stream().filter(p -> p.origin().refundable()).toList();
        var refund = Mixture.ordered(parts);
        if (!refund.empty()) {
            var tickets = new ArrayList<>(castingLedger.pending());
            tickets.add(new RefundTicket(sequence++, refund));
            castingLedger =
                    new CastingLedger(castingLedger.refunded(), castingLedger.failures(), tickets);
        }
        loss += mana.total() - refund.total();
        changed();
    }

    private void storage(int i) {
        Rune rune = nodes.get(i);
        var settings = rune.storage();
        for (int edge : manaIncoming[i])
            if (inbox[edge] != null) {
                Packet incoming = inbox[edge];
                long free = settings.capacity() - (local[i] == null ? 0 : local[i].mana().total());
                long accepted = Math.min(free, incoming.mana().total());
                Mixture remainder = incoming.mana().minus(incoming.mana().takeOrdered(accepted));
                if (!canReturn(remainder)) continue;
                try {
                    if (accepted > 0) {
                        var p = new Packet(incoming.mana().takeOrdered(accepted), incoming.spell());
                        local[i] = local[i] == null ? p : local[i].merge(p);
                    }
                    inbox[edge] = null;
                    returnUnused(remainder);
                    phaseMoved = true;
                } catch (IllegalArgumentException incompatible) {
                    if (canReturn(incoming.mana())) {
                        returnUnused(incoming.mana());
                        inbox[edge] = null;
                    }
                }
            }
        if (!powered
                || !gates[i]
                || clock < wakeTicks[i]
                || local[i] == null
                || local[i].mana().total() < settings.threshold()
                || manaOutgoing[i].length == 0) return;
        for (int edge : manaOutgoing[i]) if (pending[edge] != null) return;
        var batch = local[i];
        var released = batch.mana().takeOrdered(settings.release());
        var left = batch.mana().minus(released);
        var shares = released.split(splitWeights[i]);
        for (int j = 0; j < manaOutgoing[i].length; j++) {
            int edge = manaOutgoing[i][j];
            var after = blueprint.routes().get(edge).transmit(shares.get(j), blueprint.rules());
            loss += shares.get(j).total() - after.total();
            if (!after.empty()) pending[edge] = new Packet(after, batch.spell());
        }
        local[i] = left.empty() ? null : new Packet(left, batch.spell());
        wakeTicks[i] = clock + 1;
        phaseMoved = true;
    }

    private boolean persistent(int i) {
        return switch (nodes.get(i).kind()) {
            case STORAGE, AND, SWITCH, SENSOR, MANA_RECEIVE, MANA_SEND, FILTER, SELECT -> true;
            case OUTPUT -> wakeTicks[i] > clock;
            default -> false;
        };
    }

    private void expireOrdinary() {
        for (int i = 0; i < nodes.size(); i++)
            if (!persistent(i)) {
                if (local[i] != null && canReturn(local[i].mana())) {
                    returnUnused(local[i].mana());
                    local[i] = null;
                }
                for (int edge : manaIncoming[i])
                    if (inbox[edge] != null && canReturn(inbox[edge].mana())) {
                        returnUnused(inbox[edge].mana());
                        inbox[edge] = null;
                    }
                if (nodes.get(i).kind() == Rune.Kind.OUTPUT
                        && Arrays.stream(manaIncoming[i]).noneMatch(e -> inbox[e] != null))
                    wakeTicks[i] = 0;
            }
    }

    private static List<Mixture> proportionalTake(Mixture mana, long amount) {
        Mixture paid = mana.take(amount);
        return List.of(paid, mana.minus(paid));
    }

    public TickCircuit withCasting(CastingRules policy) {
        if (blueprint.rules().casting().equals(policy)) return this;
        CircuitSnapshot s = snapshot();
        Blueprint b =
                new Blueprint(
                        blueprint.version(),
                        blueprint.rules().withCasting(policy),
                        blueprint.runes(),
                        blueprint.routes(),
                        blueprint.continuous());
        return restore(
                new CircuitSnapshot(
                        14,
                        b,
                        s.clock(),
                        s.sequence(),
                        s.powered(),
                        s.remaining(),
                        s.injectionCountdown(),
                        s.supplied(),
                        s.loss(),
                        s.delivered(),
                        s.reserve(),
                        s.local(),
                        s.inbox(),
                        s.transit(),
                        s.pending(),
                        s.signals(),
                        s.gates(),
                        s.ports(),
                        s.drives(),
                        s.emitted(),
                        s.routeValidity(),
                        s.driveValidity(),
                        s.emittedValidity(),
                        s.logic(),
                        s.arrivalSequence(),
                        s.castingLedger(),
                        s.wakeTicks(),
                        s.supplyMode(),
                        s.charge()));
    }

    /** Explicit timing migration; retain packets, ledger and complete control state. */
    public TickCircuit withManaSteps(int steps) {
        if (blueprint.rules().manaSteps() == steps && blueprint.rules().version() >= 3) return this;
        CircuitSnapshot s = snapshot();
        Blueprint b =
                new Blueprint(
                        blueprint.version(),
                        blueprint.rules().withManaSteps(steps),
                        blueprint.runes(),
                        blueprint.routes(),
                        blueprint.continuous());
        return restore(
                new CircuitSnapshot(
                        14,
                        b,
                        s.clock(),
                        s.sequence(),
                        s.powered(),
                        s.remaining(),
                        s.injectionCountdown(),
                        s.supplied(),
                        s.loss(),
                        s.delivered(),
                        s.reserve(),
                        s.local(),
                        s.inbox(),
                        s.transit(),
                        s.pending(),
                        s.signals(),
                        s.gates(),
                        s.ports(),
                        s.drives(),
                        s.emitted(),
                        s.routeValidity(),
                        s.driveValidity(),
                        s.emittedValidity(),
                        s.logic(),
                        s.arrivalSequence(),
                        s.castingLedger(),
                        s.wakeTicks(),
                        s.supplyMode(),
                        s.charge()));
    }
}
