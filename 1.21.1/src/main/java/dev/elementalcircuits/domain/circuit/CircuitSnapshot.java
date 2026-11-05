package dev.elementalcircuits.domain.circuit;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.casting.CastingLedger;
import dev.elementalcircuits.domain.logic.RuneLogicState;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.domain.mana.Packet;

import java.util.*;

/** Detached complete runtime state; independent of a concrete VM implementation. */
public record CircuitSnapshot(
        int version,
        Blueprint blueprint,
        long clock,
        long sequence,
        boolean powered,
        int remaining,
        int injectionCountdown,
        long supplied,
        long loss,
        long delivered,
        Mixture reserve,
        Packet[] local,
        Packet[] inbox,
        Packet[] transit,
        Packet[] pending,
        boolean[] signals,
        boolean[] gates,
        Packet[] ports,
        boolean[] drives,
        boolean[] emitted,
        boolean[] routeValidity,
        boolean[] driveValidity,
        boolean[] emittedValidity,
        List<RuneLogicState> logic,
        long arrivalSequence,
        CastingLedger castingLedger,
        long[] wakeTicks,
        SupplyMode supplyMode,
        long charge) {
    public CircuitSnapshot(
            int version,
            Blueprint blueprint,
            long clock,
            long sequence,
            boolean powered,
            int remaining,
            int injectionCountdown,
            long supplied,
            long loss,
            long delivered,
            Mixture reserve,
            Packet[] local,
            Packet[] inbox,
            Packet[] transit,
            Packet[] pending,
            boolean[] signals,
            boolean[] gates,
            Packet[] ports,
            boolean[] drives,
            boolean[] emitted,
            boolean[] routeValidity,
            boolean[] driveValidity,
            boolean[] emittedValidity,
            List<RuneLogicState> logic,
            long arrivalSequence,
            CastingLedger castingLedger,
            long[] wakeTicks) {
        this(
                version,
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
                logic,
                arrivalSequence,
                castingLedger,
                wakeTicks,
                SupplyMode.CONFIGURED,
                Fixed.ONE);
    }

    public CircuitSnapshot(
            int version,
            Blueprint blueprint,
            long clock,
            long sequence,
            boolean powered,
            int remaining,
            int injectionCountdown,
            long supplied,
            long loss,
            long delivered,
            Mixture reserve,
            Packet[] local,
            Packet[] inbox,
            Packet[] transit,
            Packet[] pending,
            boolean[] signals,
            boolean[] gates,
            Packet[] ports,
            boolean[] drives,
            boolean[] emitted,
            boolean[] routeValidity,
            boolean[] driveValidity,
            boolean[] emittedValidity,
            List<RuneLogicState> logic,
            long arrivalSequence,
            CastingLedger castingLedger) {
        this(
                version,
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
                logic,
                arrivalSequence,
                castingLedger,
                new long[local.length]);
    }

    public CircuitSnapshot(
            int version,
            Blueprint blueprint,
            long clock,
            long sequence,
            boolean powered,
            int remaining,
            int injectionCountdown,
            long supplied,
            long loss,
            long delivered,
            Mixture reserve,
            Packet[] local,
            Packet[] inbox,
            Packet[] transit,
            Packet[] pending,
            boolean[] signals,
            boolean[] gates) {
        this(
                version,
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
                new Packet[local.length],
                new boolean[local.length],
                new boolean[local.length],
                truth(signals.length),
                new boolean[local.length],
                new boolean[local.length],
                initialLogic(blueprint));
    }

    public CircuitSnapshot(
            int version,
            Blueprint blueprint,
            long clock,
            long sequence,
            boolean powered,
            int remaining,
            int injectionCountdown,
            long supplied,
            long loss,
            long delivered,
            Mixture reserve,
            Packet[] local,
            Packet[] inbox,
            Packet[] transit,
            Packet[] pending,
            boolean[] signals,
            boolean[] gates,
            Packet[] ports,
            boolean[] drives,
            boolean[] emitted,
            boolean[] routeValidity,
            boolean[] driveValidity,
            boolean[] emittedValidity) {
        this(
                version,
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
                initialLogic(blueprint));
    }

    /**
     * Compatibility constructor for explicit state builders; production snapshots pass the counter.
     */
    public CircuitSnapshot(
            int version,
            Blueprint blueprint,
            long clock,
            long sequence,
            boolean powered,
            int remaining,
            int injectionCountdown,
            long supplied,
            long loss,
            long delivered,
            Mixture reserve,
            Packet[] local,
            Packet[] inbox,
            Packet[] transit,
            Packet[] pending,
            boolean[] signals,
            boolean[] gates,
            Packet[] ports,
            boolean[] drives,
            boolean[] emitted,
            boolean[] routeValidity,
            boolean[] driveValidity,
            boolean[] emittedValidity,
            List<RuneLogicState> logic) {
        this(
                version,
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
                logic,
                latest(reserve, local, inbox, transit, pending, ports));
    }

    public CircuitSnapshot(
            int version,
            Blueprint blueprint,
            long clock,
            long sequence,
            boolean powered,
            int remaining,
            int injectionCountdown,
            long supplied,
            long loss,
            long delivered,
            Mixture reserve,
            Packet[] local,
            Packet[] inbox,
            Packet[] transit,
            Packet[] pending,
            boolean[] signals,
            boolean[] gates,
            Packet[] ports,
            boolean[] drives,
            boolean[] emitted,
            boolean[] routeValidity,
            boolean[] driveValidity,
            boolean[] emittedValidity,
            List<RuneLogicState> logic,
            long arrivalSequence) {
        this(
                version,
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
                logic,
                arrivalSequence,
                CastingLedger.EMPTY);
    }

    private static long latest(Mixture reserve, Packet[]... groups) {
        long value = reserve.latestArrival();
        for (var group : groups)
            for (var p : group) if (p != null) value = Math.max(value, p.mana().latestArrival());
        return value;
    }

    private static boolean[] truth(int size) {
        boolean[] b = new boolean[size];
        Arrays.fill(b, true);
        return b;
    }

    public CircuitSnapshot {
        Objects.requireNonNull(castingLedger);
        Objects.requireNonNull(supplyMode);
        wakeTicks = wakeTicks.clone();
        logic = List.copyOf(logic);
        local = local.clone();
        inbox = inbox.clone();
        transit = transit.clone();
        pending = pending.clone();
        signals = signals.clone();
        gates = gates.clone();
        ports = ports.clone();
        drives = drives.clone();
        emitted = emitted.clone();
        routeValidity = routeValidity.clone();
        driveValidity = driveValidity.clone();
        emittedValidity = emittedValidity.clone();
    }

    @Override
    public long[] wakeTicks() {
        return wakeTicks.clone();
    }

    @Override
    public Packet[] local() {
        return local.clone();
    }

    @Override
    public Packet[] inbox() {
        return inbox.clone();
    }

    @Override
    public Packet[] transit() {
        return transit.clone();
    }

    @Override
    public Packet[] pending() {
        return pending.clone();
    }

    @Override
    public boolean[] signals() {
        return signals.clone();
    }

    @Override
    public boolean[] gates() {
        return gates.clone();
    }

    @Override
    public Packet[] ports() {
        return ports.clone();
    }

    @Override
    public boolean[] drives() {
        return drives.clone();
    }

    @Override
    public boolean[] emitted() {
        return emitted.clone();
    }

    @Override
    public boolean[] routeValidity() {
        return routeValidity.clone();
    }

    @Override
    public boolean[] driveValidity() {
        return driveValidity.clone();
    }

    @Override
    public boolean[] emittedValidity() {
        return emittedValidity.clone();
    }

    private static List<RuneLogicState> initialLogic(Blueprint blueprint) {
        return blueprint.runes().stream()
                .sorted(Comparator.comparingInt(Rune::id))
                .map(RuneLogicState::initial)
                .toList();
    }
}
