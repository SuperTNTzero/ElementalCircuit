package dev.elementalcircuits.domain;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/** Immutable component ownership. Pure mana is distinct from arcane. */
public final class Mixture {
    public static final int COMPONENTS = 17;
    public static final Mixture EMPTY = new Mixture(new long[COMPONENTS]);
    private final long[] values;
    public Mixture(long[] values) {
        if (values.length != COMPONENTS) throw new IllegalArgumentException("元素数量错误");
        this.values = values.clone();
        long total = 0;
        for (long value : values) total = Fixed.add(total, Fixed.checked(value));
    }
    public static Mixture pure(long amount) { long[] v = new long[COMPONENTS]; v[0] = Fixed.checked(amount); return new Mixture(v); }
    public long[] values() { return values.clone(); }
    public long total() { long result = 0; for (long value : values) result += value; return result; }
    public boolean empty() { return total() == 0; }
    public Mixture plus(Mixture other) {
        long[] v = values();
        for (int i = 0; i < v.length; i++) v[i] = Fixed.add(v[i], other.values[i]);
        return new Mixture(v);
    }
    public Mixture minus(Mixture other) {
        long[] v = values();
        for (int i = 0; i < v.length; i++) v[i] = Fixed.checked(v[i] - other.values[i]);
        return new Mixture(v);
    }
    public Mixture retain(long rate) {
        long[] v = values();
        for (int i = 0; i < v.length; i++) v[i] = Fixed.mul(v[i], rate);
        return new Mixture(v);
    }
    /** Exact proportional payment; remainder follows component order, including at MAX. */
    public Mixture take(long amount) {
        if (amount < 0 || amount > total()) throw new IllegalArgumentException("支取量越界");
        if (amount == 0) return EMPTY;
        long[] paid = values(); long left = amount;
        java.math.BigInteger divisor = java.math.BigInteger.valueOf(total());
        for (int i=0;i<paid.length;i++) {
            paid[i] = java.math.BigInteger.valueOf(paid[i]).multiply(java.math.BigInteger.valueOf(amount)).divide(divisor).longValueExact();
            left -= paid[i];
        }
        for (int i=0;left>0;i=(i+1)%paid.length) if (paid[i]<values[i]) { paid[i]++; left--; }
        return new Mixture(paid);
    }
    public Mixture convert(Element element, long rate) {
        long[] v = retain(rate).values();
        if (element != Element.PURE) { v[element.component(false)] += v[0]; v[0] = 0; }
        return new Mixture(v);
    }
    public Mixture inverse(long rate) {
        long[] v = retain(rate).values();
        for (int i = 1; i < v.length; i += 2) { long x = v[i]; v[i] = v[i + 1]; v[i + 1] = x; }
        return new Mixture(v);
    }
    public boolean opposed() {
        for (int i = 1; i < values.length; i += 2) if (values[i] > 0 && values[i + 1] > 0) return true;
        return present(Element.EARTH) && present(Element.WIND) || present(Element.WATER) && present(Element.FIRE);
    }
    private boolean present(Element e) { return values[e.component(false)] + values[e.component(true)] > 0; }
    public Element dominant() {
        Element result = Element.PURE; long max = values[0];
        for (Element e : Element.values()) if (e != Element.PURE) {
            long a = values[e.component(false)] + values[e.component(true)];
            if (a > max) { max = a; result = e; }
        }
        return result;
    }
    /** Per-component weighted division, remainder in saved edge order. */
    public List<Mixture> split(int[] weights) {
        long sum = 0;
        for (int w : weights) { if (w < 0 || w > 1000) throw new IllegalArgumentException("权重越界"); sum += w; }
        if (sum == 0) throw new IllegalArgumentException("至少一个出口权重大于零");
        long[][] parts = new long[weights.length][COMPONENTS];
        for (int c = 0; c < COMPONENTS; c++) {
            long left = values[c];
            for (int i = 0; i < weights.length; i++) { parts[i][c] = values[c] * weights[i] / sum; left -= parts[i][c]; }
            for (int i = 0; left > 0; i = (i + 1) % weights.length) if (weights[i] > 0) { parts[i][c]++; left--; }
        }
        List<Mixture> result = new ArrayList<>();
        for (long[] part : parts) result.add(new Mixture(part));
        return List.copyOf(result);
    }
    @Override public boolean equals(Object o) { return o instanceof Mixture m && Arrays.equals(values, m.values); }
    @Override public int hashCode() { return Arrays.hashCode(values); }
}
