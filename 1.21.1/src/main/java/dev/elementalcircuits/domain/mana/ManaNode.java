package dev.elementalcircuits.domain.mana;

import dev.elementalcircuits.domain.Fixed;

/** Finite source reservoir. Loading, placing and inspecting a node never add energy. */
public final class ManaNode {
    public enum Kind {
        NATURAL,
        ARTIFICIAL
    }

    public record State(
            int version,
            Kind kind,
            long initial,
            long received,
            long emitted,
            long remaining,
            long regenerated,
            int regenerationRemainder) {
        public State(
                int version, Kind kind, long initial, long received, long emitted, long remaining) {
            this(version, kind, initial, received, emitted, remaining, 0, 0);
        }
    }

    private final ManaFieldRules rules;
    private final Kind kind;
    private final long initial;
    private long received, emitted, remaining, regenerated;
    private int regenerationRemainder;

    public ManaNode(ManaFieldRules rules, Kind kind) {
        this.rules = java.util.Objects.requireNonNull(rules);
        this.kind = java.util.Objects.requireNonNull(kind);
        initial = kind == Kind.NATURAL ? rules.naturalCapacity() : 0;
        remaining = initial;
    }

    public Kind kind() {
        return kind;
    }

    public long remaining() {
        return remaining;
    }

    public long capacity() {
        return kind == Kind.NATURAL ? rules.naturalCapacity() : rules.artificialCapacity();
    }

    public boolean canInsert() {
        return kind == Kind.ARTIFICIAL
                && remaining <= capacity() - rules.crystal()
                && received <= Long.MAX_VALUE - rules.crystal();
    }

    public boolean insert() {
        if (!canInsert()) return false;
        remaining += rules.crystal();
        received += rules.crystal();
        return true;
    }

    public long emission() {
        return Math.min(
                remaining,
                kind == Kind.NATURAL ? rules.naturalPerStep() : rules.artificialPerStep());
    }

    public void emitted(long amount) {
        emitted(amount, kind == Kind.NATURAL ? rules.naturalPerStep() : rules.artificialPerStep());
    }

    public void emitted(long amount, long stepLimit) {
        Fixed.checked(stepLimit);
        Fixed.checked(amount);
        if (amount > Math.min(remaining, stepLimit) || emitted > Long.MAX_VALUE - amount)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_node.001"));
        remaining -= amount;
        emitted += amount;
    }

    public long regenerate(long perSecond, int ticks) {
        Fixed.checked(perSecond);
        if (ticks < 1 || ticks > 20)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_node.002"));
        if (kind != Kind.NATURAL) return 0;
        if (remaining == capacity()) {
            regenerationRemainder = 0;
            return 0;
        }
        long numerator = perSecond * ticks + regenerationRemainder;
        long gained = Math.min(capacity() - remaining, numerator / 20);
        long next = Math.addExact(regenerated, gained);
        remaining += gained;
        regenerated = next;
        regenerationRemainder = remaining == capacity() ? 0 : (int) (numerator % 20);
        return gained;
    }

    public State snapshot() {
        return new State(
                2, kind, initial, received, emitted, remaining, regenerated, regenerationRemainder);
    }

    public static ManaNode restore(ManaFieldRules rules, State state) {
        if (state == null || (state.version() < 1 || state.version() > 2) || state.kind() == null)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_node.003"));
        ManaNode node = new ManaNode(rules, state.kind());
        if (state.initial() != node.initial
                || state.received() < 0
                || state.emitted() < 0
                || state.remaining() < 0
                || state.remaining() > node.capacity()
                || state.kind() == Kind.NATURAL && state.received() != 0
                || Math.addExact(
                                Math.addExact(state.initial(), state.received()),
                                state.regenerated())
                        != Math.addExact(state.emitted(), state.remaining()))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_node.004"));
        if (state.regenerated() < 0
                || state.regenerationRemainder() < 0
                || state.regenerationRemainder() >= 20
                || state.version() == 1
                        && (state.regenerated() != 0 || state.regenerationRemainder() != 0)
                || state.kind() == Kind.ARTIFICIAL
                        && (state.regenerated() != 0 || state.regenerationRemainder() != 0))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_node.005"));
        node.regenerated = state.regenerated();
        node.regenerationRemainder = state.regenerationRemainder();
        node.received = state.received();
        node.emitted = state.emitted();
        node.remaining = state.remaining();
        return node;
    }
}
