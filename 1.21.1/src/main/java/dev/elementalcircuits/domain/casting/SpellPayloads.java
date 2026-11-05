package dev.elementalcircuits.domain.casting;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.domain.mana.Packet;

import java.util.*;

/** Split phase traversal and prepaid illumination without copying any component. */
public final class SpellPayloads {
    public record Lit(Packet impact, int ticks) {}

    private SpellPayloads() {}

    public static boolean phased(Mixture mana) {
        long[] values = mana.values();
        long arcane = values[Element.ARCANE.component(false)];
        return arcane > 0 && mana.total() == arcane;
    }

    public static List<Packet> split(Packet packet) {
        long[] values = packet.mana().values();
        long arcane = values[Element.ARCANE.component(false)];
        // Keep the FIFO reaction together until contact. Phase splitting must not rescue arcane
        // from its inverse, and unreacted mixed negation never gains pure-arcane traversal.
        if (values[Element.ARCANE.component(true)] > 0) return List.of(packet);
        if (arcane == 0 || arcane == packet.mana().total()) return List.of(packet);
        long[] phase = new long[17];
        phase[Element.ARCANE.component(false)] = arcane;
        values[Element.ARCANE.component(false)] = 0;
        return List.of(
                new Packet(packet.mana().minus(packet.mana().select(phase)), packet.spell()),
                new Packet(packet.mana().select(phase), packet.spell()));
    }

    public static Lit illumination(Packet packet, long strength, RadianceRules rules) {
        if (strength < Fixed.ONE / 4 || strength > 2 * Fixed.ONE)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._spell_payloads.001"));
        long[] values = packet.mana().values();
        // Preserve potential counter-reagents until contact instead of spending light on a trail.
        if (values[Element.ARCANE.component(true)] > 0) return new Lit(packet, 0);
        long reserved = values[Element.LIGHT.component(false)] / 4;
        var scale = packet.spell().effectScale();
        int ticks =
                (int)
                        Math.min(
                                128,
                                scale.effectAmount(reserved)
                                        * strength
                                        / Fixed.ONE
                                        / rules.trailCost());
        if (ticks == 0) return new Lit(packet, 0);
        long nominal = (ticks * rules.trailCost() * Fixed.ONE + strength - 1) / strength;
        long paid = scale.paidAmount(nominal);
        values[Element.LIGHT.component(false)] -= paid;
        return new Lit(new Packet(packet.mana().select(values), packet.spell()), ticks);
    }
}
