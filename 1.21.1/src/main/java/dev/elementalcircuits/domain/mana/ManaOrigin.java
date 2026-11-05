package dev.elementalcircuits.domain.mana;

import java.util.Objects;

/** Stable source address; not authority to mint mana. UNKNOWN legacy mana is never refunded. */
public record ManaOrigin(String key) {
    public static final ManaOrigin UNKNOWN = new ManaOrigin("unknown");

    public ManaOrigin {
        Objects.requireNonNull(key);
        if (key.length() > 192 || !key.matches("[a-zA-Z0-9_:./,-]+"))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_origin.001"));
    }

    public boolean refundable() {
        return !key.equals("unknown");
    }
}
