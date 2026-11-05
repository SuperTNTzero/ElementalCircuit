package dev.elementalcircuits.domain.mana;

import dev.elementalcircuits.domain.Fixed;

/** Server-owned source. Debit must be all-or-nothing, including its recovery delay. */
public interface ManaSource {
    ManaSource NONE =
            new ManaSource() {
                public long available() {
                    return 0;
                }

                public boolean debit(long amount) {
                    Fixed.checked(amount);
                    return amount == 0;
                }
            };

    default ManaOrigin provenance() {
        return ManaOrigin.UNKNOWN;
    }

    default Mixture preview(long amount) {
        return Mixture.pure(amount).sourced(provenance());
    }

    default Mixture draw(long amount) {
        Fixed.checked(amount);
        Mixture quoted = preview(amount);
        if (quoted == null || quoted.total() != amount)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_source.001"));
        return debit(amount) ? quoted : null;
    }

    long available();

    boolean debit(long amount);
}
