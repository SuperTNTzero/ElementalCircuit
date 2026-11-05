package dev.elementalcircuits.domain.casting;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.mana.Mixture;

import java.util.*;

/** One resource allocation shared by direct impact, fragments, chain and hit modifiers. */
public final class SecondaryBudget {
    public record Contact(Mixture impact, Mixture fragments, Mixture chain) {}

    public record Hit(Mixture elemental, List<Mixture> modifiers) {
        public Hit {
            modifiers = List.copyOf(modifiers);
        }
    }

    private SecondaryBudget() {}

    public static Contact contact(Mixture mana, boolean split, boolean chain) {
        var a = mana.split(new int[] {1, split ? 1 : 0, chain ? 1 : 0});
        return new Contact(a.get(0), a.get(1), a.get(2));
    }

    public static Hit hit(Mixture mana, ProjectileEffects e) {
        if (!e.hit()) return new Hit(mana, List.of(Mixture.EMPTY, Mixture.EMPTY, Mixture.EMPTY));
        var sides = mana.split(new int[] {1, 1});
        var modifiers =
                sides.get(1)
                        .split(
                                new int[] {
                                    e.ignite() > 0 ? 1 : 0,
                                    e.slow() > 0 ? 1 : 0,
                                    e.knockback() > 0 ? 1 : 0
                                });
        return new Hit(sides.get(0), modifiers);
    }

    public static int duration(long amount, long strength, int requested, SecondaryRules rules) {
        return (int)
                Math.min(requested * 20L, amount * strength / Fixed.ONE * 20 / rules.statusCost());
    }

    public static long impulse(long amount, long strength, int requested, SecondaryRules rules) {
        return Math.min(
                Fixed.ONE,
                Math.min(
                        requested * Fixed.ONE / 4,
                        amount * strength / Fixed.ONE * Fixed.ONE / rules.statusCost() / 4));
    }
}
