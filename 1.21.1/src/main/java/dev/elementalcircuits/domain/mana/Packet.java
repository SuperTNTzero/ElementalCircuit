package dev.elementalcircuits.domain.mana;

import dev.elementalcircuits.domain.casting.SpellSpec;

public record Packet(Mixture mana, SpellSpec spell) {
    public Packet {
        if (mana == null || spell == null || mana.empty())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._packet.001"));
    }

    public Packet merge(Packet b) {
        return new Packet(mana.mergeArrivals(b.mana), spell.merge(b.spell));
    }
}
