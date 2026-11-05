package dev.elementalcircuits.domain.casting;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.mana.Mixture;

import java.math.BigInteger;
import java.util.ArrayList;

/** Output potency per paid unit. Never used to supply a circuit or fund a recursive capsule. */
public record EffectScale(long numerator, long denominator) {
    public static final EffectScale IDENTITY = new EffectScale(1, 1);

    public EffectScale {
        Fixed.checked(numerator);
        Fixed.checked(denominator);
        if (denominator == 0 || numerator < denominator)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._effect_scale.001"));
        long a = numerator, b = denominator;
        while (b != 0) {
            long r = a % b;
            a = b;
            b = r;
        }
        numerator /= a;
        denominator /= a;
    }

    public long effectAmount(long paid) {
        Fixed.checked(paid);
        return BigInteger.valueOf(paid)
                .multiply(BigInteger.valueOf(numerator))
                .divide(BigInteger.valueOf(denominator))
                .min(BigInteger.valueOf(Fixed.MAX))
                .longValueExact();
    }

    /** Round up at the payment boundary so an effect is never granted for an unpaid fraction. */
    public long paidAmount(long effect) {
        Fixed.checked(effect);
        return BigInteger.valueOf(effect)
                .multiply(BigInteger.valueOf(denominator))
                .add(BigInteger.valueOf(numerator - 1))
                .divide(BigInteger.valueOf(numerator))
                .longValueExact();
    }

    /** Contact-only view, retaining elemental order and provenance without minting source mana. */
    public Mixture apply(Mixture paid) {
        if (numerator == denominator || paid.empty()) return paid;
        long target = effectAmount(paid.total());
        var total = BigInteger.valueOf(paid.total());
        var amounts = new long[paid.order().size()];
        long remainder = target;
        for (int i = 0; i < amounts.length; i++) {
            amounts[i] =
                    BigInteger.valueOf(paid.order().get(i).amount())
                            .multiply(BigInteger.valueOf(target))
                            .divide(total)
                            .longValueExact();
            remainder -= amounts[i];
        }
        var portions = new ArrayList<Mixture.Portion>();
        for (int i = 0; i < amounts.length; i++) {
            var p = paid.order().get(i);
            long amount = amounts[i] + (remainder-- > 0 ? 1 : 0);
            if (amount > 0)
                portions.add(new Mixture.Portion(p.component(), amount, p.arrival(), p.origin()));
        }
        return Mixture.ordered(portions);
    }
}
