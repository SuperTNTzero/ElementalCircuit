package dev.elementalcircuits.domain;

public record Packet(Mixture mana, SpellSpec spell) {
    public Packet { if (mana == null || spell == null || mana.empty()) throw new IllegalArgumentException("非法空包"); }
    public Packet merge(Packet b) { return new Packet(mana.plus(b.mana), spell.merge(b.spell)); }
}
