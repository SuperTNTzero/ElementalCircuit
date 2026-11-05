package dev.elementalcircuits.domain.circuit;

/**
 * Format and arithmetic ceilings. Server admission budgets may be lower; never consulted during
 * restore.
 */
public final class CircuitBounds {
    public static final int RUNES = 4096, ROUTES = 8192, PATH = 4096, BOARD = 128;
    public static final int MANA_SEGMENTS = 16384;
    public static final int DELAY_TICKS = 65536;
    public static final int RANGE = 1024, RADIUS = 32, DERIVATIVES = 64, DURATION = 3600;

    private CircuitBounds() {}
}
