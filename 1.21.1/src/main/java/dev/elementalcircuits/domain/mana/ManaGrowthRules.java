package dev.elementalcircuits.domain.mana;

import dev.elementalcircuits.domain.Fixed;

/** Player progression, independent of circuit configuration and item rendering. */
public record ManaGrowthRules(int version, long fruitIncrease, long maximum) {
    public ManaGrowthRules {
        if ((version != 1 && version != 2)
                || version == 1 && maximum != 1024 * Fixed.ONE
                || maximum < Fixed.ONE
                || maximum > Fixed.MAX
                || fruitIncrease < Fixed.ONE
                || fruitIncrease > maximum
                || fruitIncrease % Fixed.ONE != 0)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_growth_rules.001"));
    }

    public boolean canGrow(ManaPool pool) {
        return pool.permanentCapacity() < maximum;
    }

    public boolean apply(ManaPool pool) {
        return pool.increaseCapacity(fruitIncrease, maximum);
    }
}
