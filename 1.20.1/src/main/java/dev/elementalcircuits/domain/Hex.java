package dev.elementalcircuits.domain;

public record Hex(int q, int r) {
    public Hex { if (Math.abs((long) q) > 32 || Math.abs((long) r) > 32) throw new IllegalArgumentException("格坐标越界"); }
    public int distance(Hex other) { return (Math.abs(q - other.q) + Math.abs(r - other.r) + Math.abs(q + r - other.q - other.r)) / 2; }
    public boolean inside(int radius) { return Math.max(Math.max(Math.abs(q), Math.abs(r)), Math.abs(q + r)) <= radius; }
}
