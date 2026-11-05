package dev.elementalcircuits.domain.mana;

import dev.elementalcircuits.domain.Fixed;

/** Explicit persistent mana, independent from transport packet limits. */
public record StorageSettings(long capacity, long threshold, long release) {
    public static final StorageSettings NONE = new StorageSettings(0, 0, 0);
    public static final StorageSettings DEFAULT =
            new StorageSettings(64 * Fixed.ONE, 4 * Fixed.ONE, 4 * Fixed.ONE);

    public StorageSettings {
        Fixed.checked(capacity);
        Fixed.checked(threshold);
        Fixed.checked(release);
        if (capacity != 0 || threshold != 0 || release != 0)
            if (release < 1 || threshold < release || capacity < threshold)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.mana._storage_settings.001"));
    }
}
