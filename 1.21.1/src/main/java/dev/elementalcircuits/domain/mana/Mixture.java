package dev.elementalcircuits.domain.mana;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.circuit.CircuitBounds;
import dev.elementalcircuits.domain.combat.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Immutable component ownership. Pure mana is distinct from arcane. */
public final class Mixture {
    public static final int COMPONENTS = 17;
    public static final Mixture EMPTY = new Mixture(new long[COMPONENTS]);

    public record Portion(int component, long amount, long arrival, ManaOrigin origin) {
        public Portion(int component, long amount, long arrival) {
            this(component, amount, arrival, ManaOrigin.UNKNOWN);
        }

        public Portion {
            if (origin == null
                    || component < 0
                    || component >= COMPONENTS
                    || amount < 1
                    || amount > Fixed.MAX
                    || arrival < 0
                    || arrival == Long.MAX_VALUE)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.mana._mixture.001"));
        }
    }

    private final int orderVersion = 2;
    private final long[] values;
    private final List<Portion> order;

    /** Canonical order is only for explicit vector construction and historical amount-only data. */
    public Mixture(long[] values) {
        if (values.length != COMPONENTS)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mixture.002"));
        this.values = values.clone();
        long total = 0;
        var parts = new ArrayList<Portion>();
        for (int c = 0; c < values.length; c++) {
            total = Fixed.add(total, Fixed.checked(values[c]));
            if (values[c] > 0) parts.add(new Portion(c, values[c], 0));
        }
        order = List.copyOf(parts);
    }

    private Mixture(List<Portion> source) {
        values = new long[COMPONENTS];
        var parts = new ArrayList<Portion>();
        long total = 0;
        for (var p : source) {
            total = Fixed.add(total, p.amount());
            values[p.component()] = Fixed.add(values[p.component()], p.amount());
            if (!parts.isEmpty()) {
                var last = parts.getLast();
                if (last.component() == p.component()
                        && last.arrival() == p.arrival()
                        && last.origin().equals(p.origin())) {
                    parts.set(
                            parts.size() - 1,
                            new Portion(
                                    p.component(),
                                    last.amount() + p.amount(),
                                    p.arrival(),
                                    p.origin()));
                    continue;
                }
            }
            if (parts.size() >= CircuitBounds.MANA_SEGMENTS)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.mana._mixture.003"));
            parts.add(p);
        }
        order = List.copyOf(parts);
    }

    public static Mixture ordered(List<Portion> source) {
        return new Mixture(List.copyOf(source));
    }

    public List<Portion> order() {
        return order;
    }

    public boolean sameAmounts(Mixture other) {
        return Arrays.equals(values, other.values);
    }

    public long latestArrival() {
        return order.stream().mapToLong(Portion::arrival).max().orElse(0);
    }

    /** Stamp at the destination boundary; retain the order inside this arriving packet. */
    public Mixture arriving(long sequence) {
        var out = new ArrayList<Portion>();
        for (var p : order) out.add(new Portion(p.component(), p.amount(), sequence, p.origin()));
        return ordered(out);
    }

    public Mixture mergeArrivals(Mixture other) {
        var parts = new ArrayList<Portion>(order);
        parts.addAll(other.order);
        parts.sort(java.util.Comparator.comparingLong(Portion::arrival));
        return ordered(parts);
    }

    public static Mixture pure(long amount) {
        long[] v = new long[COMPONENTS];
        v[0] = Fixed.checked(amount);
        return new Mixture(v);
    }

    /**
     * Allocate these component quotas from oldest portions; never synthesize a new element order.
     */
    public Mixture select(long[] quotas) {
        if (quotas.length != COMPONENTS)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mixture.004"));
        long[] remaining = quotas.clone();
        for (int c = 0; c < COMPONENTS; c++)
            if (remaining[c] < 0 || remaining[c] > values[c])
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.mana._mixture.005"));
        var out = new ArrayList<Portion>();
        for (var p : order) {
            long n = Math.min(p.amount(), remaining[p.component()]);
            remaining[p.component()] -= n;
            if (n > 0) out.add(new Portion(p.component(), n, p.arrival(), p.origin()));
        }
        return ordered(out);
    }

    public long[] values() {
        return values.clone();
    }

    public long total() {
        long result = 0;
        for (long value : values) result += value;
        return result;
    }

    public boolean empty() {
        return total() == 0;
    }

    public Mixture plus(Mixture other) {
        var parts = new ArrayList<Portion>(order);
        parts.addAll(other.order);
        return ordered(parts);
    }

    /** Subtract by component AND source; a refund may never debit another origin's share. */
    public Mixture minus(Mixture other) {
        record Key(int component, ManaOrigin origin) {}
        var remove = new java.util.HashMap<Key, Long>();
        for (var p : other.order)
            remove.merge(new Key(p.component(), p.origin()), p.amount(), Long::sum);
        var out = new ArrayList<Portion>();
        for (var p : order) {
            var key = new Key(p.component(), p.origin());
            long n = Math.min(p.amount(), remove.getOrDefault(key, 0L));
            remove.put(key, remove.getOrDefault(key, 0L) - n);
            if (n < p.amount())
                out.add(new Portion(p.component(), p.amount() - n, p.arrival(), p.origin()));
        }
        if (remove.values().stream().anyMatch(v -> v != 0))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mixture.006"));
        return ordered(out);
    }

    public Mixture sourced(ManaOrigin origin) {
        var out = new ArrayList<Portion>();
        for (var p : order) out.add(new Portion(p.component(), p.amount(), p.arrival(), origin));
        return ordered(out);
    }

    /** High precision division, then one truncation per component. */
    public Mixture divide(long numerator, long denominator) {
        if (numerator < 0 || denominator < 1 || numerator > denominator)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mixture.007"));
        long[] quota = values();
        for (int c = 0; c < quota.length; c++)
            quota[c] =
                    java.math.BigInteger.valueOf(quota[c])
                            .multiply(java.math.BigInteger.valueOf(numerator))
                            .divide(java.math.BigInteger.valueOf(denominator))
                            .longValueExact();
        return select(quota);
    }

    public Mixture retain(long rate) {
        long[] v = values();
        for (int i = 0; i < v.length; i++) v[i] = Fixed.mul(v[i], rate);
        return select(v);
    }

    /** Preserve endpoint and per-cell rounding, without per-cell arrays/objects. */
    public Mixture transmit(long coupling, long retention, int length) {
        if (coupling < 1
                || coupling > Fixed.ONE
                || retention < 1
                || retention > Fixed.ONE
                || length < 1
                || length >= CircuitBounds.PATH)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mixture.008"));
        long[] result = new long[COMPONENTS];
        for (int i = 0; i < COMPONENTS; i++) {
            long value = values[i] * coupling / Fixed.ONE;
            for (int step = 0; step < length && value != 0; step++)
                value = value * retention / Fixed.ONE;
            result[i] = value * coupling / Fixed.ONE;
        }
        return select(result);
    }

    /** Exact proportional payment; remainder follows component order, including at MAX. */
    public Mixture take(long amount) {
        if (amount < 0 || amount > total())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mixture.009"));
        if (amount == 0) return EMPTY;
        long[] paid = values();
        long left = amount;
        java.math.BigInteger divisor = java.math.BigInteger.valueOf(total());
        for (int i = 0; i < paid.length; i++) {
            paid[i] =
                    java.math.BigInteger.valueOf(paid[i])
                            .multiply(java.math.BigInteger.valueOf(amount))
                            .divide(divisor)
                            .longValueExact();
            left -= paid[i];
        }
        for (int i = 0; left > 0; i = (i + 1) % paid.length)
            if (paid[i] < values[i]) {
                paid[i]++;
                left--;
            }
        return select(paid);
    }

    public Mixture convert(Element element, long rate) {
        var out = new ArrayList<Portion>();
        for (var p : retain(rate).order) {
            int c =
                    p.component() == 0 && element != Element.PURE
                            ? element.component(false)
                            : p.component();
            out.add(new Portion(c, p.amount(), p.arrival(), p.origin()));
        }
        return ordered(out);
    }

    /**
     * Prefix withdrawal for a pure supply funded by a mixed capsule; exact across consecutive
     * draws.
     */
    public Mixture takeOrdered(long amount) {
        if (amount < 0 || amount > total())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mixture.010"));
        var out = new ArrayList<Portion>();
        long left = amount;
        for (var p : order) {
            long n = Math.min(left, p.amount());
            if (n > 0) out.add(new Portion(p.component(), n, p.arrival(), p.origin()));
            left -= n;
            if (left == 0) break;
        }
        return ordered(out);
    }

    public Mixture pureOrigins() {
        return ordered(
                order.stream()
                        .map(p -> new Portion(0, p.amount(), p.arrival(), p.origin()))
                        .toList());
    }

    public Mixture inverse(long rate) {
        var out = new ArrayList<Portion>();
        for (var p : retain(rate).order) {
            int c =
                    p.component() == 0
                            ? 0
                            : (p.component() % 2 == 1 ? p.component() + 1 : p.component() - 1);
            out.add(new Portion(c, p.amount(), p.arrival(), p.origin()));
        }
        return ordered(out);
    }

    public boolean opposed() {
        for (int i = 1; i < values.length; i += 2)
            if (values[i] > 0 && values[i + 1] > 0) return true;
        return present(Element.EARTH) && present(Element.WIND)
                || present(Element.WATER) && present(Element.FIRE);
    }

    private boolean present(Element e) {
        return values[e.component(false)] + values[e.component(true)] > 0;
    }

    public Element dominant() {
        Element result = Element.PURE;
        long max = values[0];
        for (Element e : Element.values())
            if (e != Element.PURE) {
                long a = values[e.component(false)] + values[e.component(true)];
                if (a > max) {
                    max = a;
                    result = e;
                }
            }
        return result;
    }

    /** Per-component weighted division, remainder in saved edge order. */
    public List<Mixture> split(int[] weights) {
        long sum = 0;
        for (int w : weights) {
            if (w < 0 || w > 1000000)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.mana._mixture.011"));
            sum += w;
        }
        if (sum == 0)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mixture.012"));
        long[][] parts = new long[weights.length][COMPONENTS];
        for (int c = 0; c < COMPONENTS; c++) {
            long left = values[c];
            for (int i = 0; i < weights.length; i++) {
                parts[i][c] = values[c] * weights[i] / sum;
                left -= parts[i][c];
            }
            for (int i = 0; left > 0; i = (i + 1) % weights.length)
                if (weights[i] > 0) {
                    parts[i][c]++;
                    left--;
                }
        }
        List<Mixture> result = new ArrayList<>();
        Mixture remaining = this;
        for (long[] part : parts) {
            Mixture share = remaining.select(part);
            result.add(share);
            remaining = remaining.minus(share);
        }
        return List.copyOf(result);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Mixture m && order.equals(m.order);
    }

    @Override
    public int hashCode() {
        return order.hashCode();
    }
}
