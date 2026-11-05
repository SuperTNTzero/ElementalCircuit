package dev.elementalcircuits.domain.casting;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.CircuitSnapshot;
import dev.elementalcircuits.domain.circuit.MagicCircuit;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.mana.ManaPool;
import dev.elementalcircuits.domain.mana.ManaSource;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.domain.mana.RefundTicket;

import java.math.BigInteger;
import java.util.*;

/**
 * An isolated, single invocation paid entirely before impact; controls advance at world tick rate.
 */
public final class TriggeredCircuit {
    public record State(
            int version,
            int age,
            int limit,
            long budget,
            ManaPool.State supply,
            CircuitSnapshot circuit,
            Mixture funding) {
        public State(
                int version,
                int age,
                int limit,
                long budget,
                ManaPool.State supply,
                CircuitSnapshot circuit) {
            this(version, age, limit, budget, supply, circuit, Mixture.pure(budget));
        }
    }

    private final TickCircuit circuit;
    private final ManaPool supply;
    private final long budget;
    private final int limit;
    private int age;
    private final Mixture funding;

    public TriggeredCircuit(Blueprint definition, long budget, RecursionRules rules) {
        this(definition, Mixture.pure(budget), rules);
    }

    public TriggeredCircuit(Blueprint definition, Mixture funding, RecursionRules rules) {
        this.funding = funding;
        long budget = funding.total();
        this.budget = Fixed.checked(budget);
        if (budget == 0)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._triggered_circuit.001"));
        limit = Math.min(definition.rules().window(), rules.jobTicks());
        circuit = new TickCircuit(plan(definition, budget, limit));
        supply = new ManaPool(budget, 0, 0);
        circuit.start();
    }

    private TriggeredCircuit(State state) {
        funding = state.funding();
        circuit = TickCircuit.restore(state.circuit());
        supply = ManaPool.restore(state.supply(), 0, 0);
        budget = state.budget();
        limit = state.limit();
        age = state.age();
    }

    public static Blueprint plan(Blueprint b, long budget, int limit) {
        List<Rune> inputs =
                b.runes().stream()
                        .filter(r -> r.kind() == Rune.Kind.INPUT)
                        .sorted(Comparator.comparingInt(Rune::id))
                        .toList();
        if (inputs.isEmpty())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._triggered_circuit.002"));
        if (b.rules().casting().modern()) {
            return new Blueprint(b.version(), b.rules(), b.runes(), b.routes(), false);
        }
        long signal =
                b.runes().stream()
                                .filter(
                                        r ->
                                                r.kind().signalOut()
                                                        || r.kind() == Rune.Kind.SIGNAL_SEND
                                                        || r.kind() == Rune.Kind.SWITCH
                                                                && r.signalControl())
                                .count()
                        * b.rules().signalCost();
        long available = budget - Math.min(budget, signal * limit), total = 0;
        for (var r : inputs) total += Math.min(b.inputAmount(r), b.rules().nodeCapacity());
        if (available < inputs.size())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._triggered_circuit.003"));
        Map<Integer, Long> amounts = new HashMap<>();
        long used = 0;
        if (total <= available)
            for (var r : inputs)
                amounts.put(r.id(), Math.min(b.inputAmount(r), b.rules().nodeCapacity()));
        else {
            long remainder = available - inputs.size(), weight = total - inputs.size();
            for (var r : inputs) {
                long demand = Math.min(b.inputAmount(r), b.rules().nodeCapacity());
                long amount =
                        1
                                + (weight == 0
                                        ? 0
                                        : BigInteger.valueOf(demand - 1)
                                                .multiply(BigInteger.valueOf(remainder))
                                                .divide(BigInteger.valueOf(weight))
                                                .longValueExact());
                amounts.put(r.id(), amount);
                used += amount;
            }
            for (int i = 0; used < available; i = (i + 1) % inputs.size()) {
                var r = inputs.get(i);
                long amount = amounts.get(r.id());
                if (amount < Math.min(b.inputAmount(r), b.rules().nodeCapacity())) {
                    amounts.put(r.id(), amount + 1);
                    used++;
                }
            }
        }
        List<Rune> nodes =
                b.runes().stream()
                        .map(
                                r ->
                                        amounts.containsKey(r.id())
                                                ? new Rune(
                                                        r.id(),
                                                        r.kind(),
                                                        r.cell(),
                                                        r.element(),
                                                        amounts.get(r.id()),
                                                        r.on(),
                                                        r.signalControl(),
                                                        r.weights())
                                                : r)
                        .toList();
        return new Blueprint(5, b.rules(), nodes, b.routes(), false);
    }

    public List<MagicCircuit.Cast> step() {
        return step(CastContext.NEUTRAL, ShapeRules.protocolV1());
    }

    public List<MagicCircuit.Cast> step(CastContext context, ShapeRules shapes) {
        if (done()) return List.of();
        age++;
        return circuit.tick(
                new ManaSource() {
                    public long available() {
                        return supply.available();
                    }

                    public boolean debit(long amount) {
                        return supply.debit(amount);
                    }

                    public Mixture preview(long amount) {
                        var left = funding.minus(funding.takeOrdered(supply.snapshot().spent()));
                        return amount <= left.total()
                                ? left.takeOrdered(amount).pureOrigins()
                                : Mixture.pure(amount);
                    }
                },
                ReleasePermit.UNLIMITED,
                ManaSource.NONE,
                context,
                shapes);
    }

    public boolean done() {
        return age >= limit
                || !circuit.needsStep()
                || circuit.singleInjectionSpent() && circuit.held() == 0;
    }

    public Mixture unspentRefund() {
        return circuit.definition().rules().casting().modern()
                ? circuit.definition()
                        .rules()
                        .casting()
                        .refundable(funding.minus(funding.takeOrdered(supply.snapshot().spent())))
                : Mixture.EMPTY;
    }

    public List<RefundTicket> refunds() {
        return circuit.refunds();
    }

    public void acknowledgeRefund(long sequence) {
        circuit.acknowledgeRefund(sequence);
    }

    public long unspent() {
        return supply.available();
    }

    public State state() {
        return new State(2, age, limit, budget, supply.snapshot(), circuit.snapshot(), funding);
    }

    public static TriggeredCircuit restore(State s, Blueprint expected, RecursionRules rules) {
        if (s == null
                || s.version() != 2
                || s.funding() == null
                || s.funding().total() != s.budget()
                || s.age() < 0
                || s.limit() != Math.min(expected.rules().window(), rules.jobTicks())
                || s.age() > s.limit()
                || s.budget() < 1
                || s.budget() > Fixed.MAX
                || s.supply().capacity() != s.budget()
                || s.supply().amount() + s.supply().spent() != s.budget()
                || s.circuit().supplied() != s.supply().spent()
                || s.circuit().clock() != s.age()
                || !s.circuit().blueprint().equals(plan(expected, s.budget(), s.limit())))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._triggered_circuit.004"));
        return new TriggeredCircuit(s);
    }
}
