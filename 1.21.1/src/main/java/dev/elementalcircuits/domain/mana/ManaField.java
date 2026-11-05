package dev.elementalcircuits.domain.mana;

import dev.elementalcircuits.domain.Fixed;

import java.util.*;

/**
 * Sparse finite-volume scalar field. Six-face synchronous diffusion; no world or rendering
 * dependency.
 */
public final class ManaField {
    public static final int MAX_CELLS = 1048576, MAX_ACTIVE = 262144;

    public record Cell(int x, int y, int z) implements Comparable<Cell> {
        public Cell {
            if (x < -3750000 || x > 3750000 || z < -3750000 || z > 3750000 || y < -512 || y > 511)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.mana._mana_field.001"));
        }

        public static Cell at(int x, int y, int z) {
            return new Cell(Math.floorDiv(x, 8), Math.floorDiv(y, 8), Math.floorDiv(z, 8));
        }

        public Cell offset(int dx, int dy, int dz) {
            return new Cell(x + dx, y + dy, z + dz);
        }

        @Override
        public int compareTo(Cell c) {
            int v = Integer.compare(x, c.x);
            if (v == 0) v = Integer.compare(y, c.y);
            return v == 0 ? Integer.compare(z, c.z) : v;
        }
    }

    public record Sample(Cell cell, long amount) {}

    public record Snapshot(
            int version,
            ManaFieldRules rules,
            int phase,
            long ambientCreated,
            long injected,
            long extracted,
            long dissipated,
            List<Sample> cells) {
        public Snapshot {
            cells = List.copyOf(cells);
        }
    }

    private ManaFieldRules rules;
    private int version = 1;
    private final NavigableMap<Cell, Long> cells = new TreeMap<>();
    private int phase;
    private long ambientCreated, injected, extracted, dissipated;
    private Object change = new Object();

    public ManaField(ManaFieldRules rules) {
        this.rules = Objects.requireNonNull(rules);
    }

    public ManaFieldRules rules() {
        return rules;
    }

    public int phase() {
        return phase;
    }

    public Object changeToken() {
        return change;
    }

    private void changed() {
        change = new Object();
    }

    public Set<Cell> cells() {
        return Collections.unmodifiableSet(cells.navigableKeySet());
    }

    /**
     * First discovery generates the world's finite ambient endowment, once; reads never create
     * mana.
     */
    public boolean discover(Cell cell) {
        Objects.requireNonNull(cell);
        if (cells.containsKey(cell)) return false;
        if (cells.size() >= MAX_CELLS)
            throw new IllegalStateException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_field.002"));
        if (ambientCreated > Long.MAX_VALUE - injected - rules.ambient()) return false;
        long next = ambientCreated + rules.ambient();
        cells.put(cell, rules.ambient());
        ambientCreated = next;
        changed();
        return true;
    }

    public long amount(Cell cell) {
        return cells.getOrDefault(cell, 0L);
    }

    public long room(Cell cell) {
        return cells.containsKey(cell) ? rules.capacity() - amount(cell) : 0;
    }

    public long inject(Cell cell, long offered) {
        Fixed.checked(offered);
        long accepted = Math.min(room(cell), offered);
        if (accepted == 0 || injected > Long.MAX_VALUE - ambientCreated - accepted) return 0;
        long next = Math.addExact(injected, accepted);
        cells.put(cell, amount(cell) + accepted);
        injected = next;
        changed();
        return accepted;
    }

    public boolean debit(Cell cell, long quantity) {
        Fixed.checked(quantity);
        if (quantity == 0) return true;
        if (quantity > amount(cell) || extracted > Long.MAX_VALUE - quantity) return false;
        cells.put(cell, amount(cell) - quantity);
        extracted += quantity;
        changed();
        return true;
    }

    /**
     * Advances one fixed step; callers supply only loaded cells, with a hard per-step work bound.
     */
    public boolean tick(Collection<Cell> loaded) {
        Objects.requireNonNull(loaded);
        if (loaded.isEmpty()) return false;
        if (loaded.size() > MAX_ACTIVE)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_field.003"));
        if (++phase < rules.stepTicks()) {
            changed();
            return false;
        }
        phase = 0;
        NavigableSet<Cell> active = new TreeSet<>(loaded);
        active.retainAll(cells.keySet());
        Map<Cell, Long> delta = new HashMap<>();
        for (Cell from : active)
            for (int axis = 0; axis < 3; axis++) {
                if (axis == 0 && from.x() == 3750000
                        || axis == 1 && from.y() == 511
                        || axis == 2 && from.z() == 3750000) continue;
                Cell to = from.offset(axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0);
                if (!active.contains(to)) continue;
                long flow = (amount(from) - amount(to)) / rules.diffusionDivisor();
                delta.merge(from, -flow, Long::sum);
                delta.merge(to, flow, Long::sum);
            }
        long decayed = 0;
        Map<Cell, Long> next = new HashMap<>();
        for (Cell c : active) {
            long value = amount(c) + delta.getOrDefault(c, 0L),
                    decay = value / rules.decayDivisor();
            if (value < 0 || value > rules.capacity())
                throw new IllegalStateException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.mana._mana_field.004"));
            decayed = Math.addExact(decayed, decay);
            next.put(c, value - decay);
        }
        long loss = Math.addExact(dissipated, decayed);
        cells.putAll(next);
        dissipated = loss;
        changed();
        return true;
    }

    /**
     * Explicit v1 -> v2 policy transition. Excess becomes recorded dissipation; existing cells
     * never refill.
     */
    public void reconfigure(ManaFieldRules next) {
        Objects.requireNonNull(next);
        long excess = 0;
        for (long value : cells.values())
            excess = Math.addExact(excess, Math.max(0, value - next.capacity()));
        long total = Math.addExact(dissipated, excess);
        cells.replaceAll((cell, value) -> Math.min(value, next.capacity()));
        rules = next;
        phase = Math.min(phase, next.stepTicks() - 1);
        dissipated = total;
        version = 2;
        changed();
    }

    public Snapshot snapshot() {
        return new Snapshot(
                version,
                rules,
                phase,
                ambientCreated,
                injected,
                extracted,
                dissipated,
                cells.entrySet().stream().map(e -> new Sample(e.getKey(), e.getValue())).toList());
    }

    public static ManaField restore(Snapshot s) {
        if (s == null
                || (s.version() != 1 && s.version() != 2)
                || s.rules() == null
                || s.phase() < 0
                || s.phase() >= s.rules().stepTicks()
                || s.cells().size() > MAX_CELLS)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_field.005"));
        ManaField field = new ManaField(s.rules());
        long held = 0;
        for (Sample sample : s.cells()) {
            if (sample == null
                    || sample.cell() == null
                    || sample.amount() < 0
                    || sample.amount() > s.rules().capacity()
                    || field.cells.put(sample.cell(), sample.amount()) != null)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.mana._mana_field.006"));
            held = Math.addExact(held, sample.amount());
        }
        for (long count :
                new long[] {s.ambientCreated(), s.injected(), s.extracted(), s.dissipated()})
            if (count < 0)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.mana._mana_field.007"));
        if (s.version() == 1
                        && s.ambientCreated()
                                != Math.multiplyExact((long) s.cells().size(), s.rules().ambient())
                || Math.addExact(s.ambientCreated(), s.injected())
                        != Math.addExact(held, Math.addExact(s.extracted(), s.dissipated())))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_field.008"));
        field.version = s.version();
        field.phase = s.phase();
        field.ambientCreated = s.ambientCreated();
        field.injected = s.injected();
        field.extracted = s.extracted();
        field.dissipated = s.dissipated();
        return field;
    }
}
