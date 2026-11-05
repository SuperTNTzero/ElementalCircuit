package dev.elementalcircuits.domain;

/** 16.16 finite, nonnegative resource quantities; products fit in signed long. */
public final class Fixed {
    public static final long ONE = 65536;
    public static final long MAX = 65536 * ONE;

    private Fixed() {}

    public static long checked(long value) {
        if (value < 0 || value > MAX)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain._fixed.001"));
        return value;
    }

    public static long add(long a, long b) {
        return checked(Math.addExact(a, b));
    }

    public static long mul(long value, long factor) {
        checked(value);
        if (factor < 0 || factor > ONE)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain._fixed.002"));
        return value * factor / ONE;
    }

    public static String display(long value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value / (double) ONE);
    }
}
