package dev.elementalcircuits.domain.combat;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.domain.mana.Packet;

/** Element/shape semantics. Rendering never supplies targets or new spell budgets. */
public final class ElementalCombos {
    public record Rules(
            int version, int lightningJumps, int lightningReach, long lightningRetention) {
        public Rules {
            if (version != 1
                    || lightningJumps < 1
                    || lightningJumps > 4
                    || lightningReach < 1
                    || lightningReach > 8
                    || lightningRetention < 1
                    || lightningRetention > Fixed.ONE)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.combat._elemental_combos.001"));
        }
    }

    private ElementalCombos() {}

    public static boolean lightningBeam(Packet packet) {
        var e = packet.spell().effects();
        return packet.spell().method() == SpellSpec.Method.DIRECTED
                && packet.spell().shape() == SpellSpec.Shape.BEAM
                && e.fragments() == 0
                && e.burst() == 0
                && packet.mana().total() > 0
                && packet.mana().values()[Element.LIGHTNING.component(false)]
                        == packet.mana().total();
    }

    public static int jumps(Packet packet, Rules rules) {
        return packet.spell().effects().chains() > 0
                ? packet.spell().effects().chains()
                : rules.lightningJumps();
    }

    public record Share(Mixture contact, Mixture onward) {}

    public static Share share(Mixture mana, boolean onward, Rules rules) {
        if (!onward) return new Share(mana, Mixture.EMPTY);
        var parts = mana.split(new int[] {1, 1});
        return new Share(parts.get(0), parts.get(1).retain(rules.lightningRetention()));
    }
}
