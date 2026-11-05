package dev.elementalcircuits.domain.casting;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.circuit.Rules;

import java.math.BigInteger;

/** Resolve defaults first, quote fixed requested effect, divide at high precision only once. */
public final class CastBudget {
    public record Quote(
            SpellSpec resolved,
            long effect,
            long required,
            long footprint,
            long parameterDivisor) {}

    private CastBudget() {}

    public static SpellSpec resolve(SpellSpec s, Rules r) {
        var p = r.casting().standard();
        return new SpellSpec(
                        s.method() == SpellSpec.Method.UNSET ? SpellSpec.Method.AREA : s.method(),
                        s.method() != SpellSpec.Method.CONTACT && s.shape() == SpellSpec.Shape.UNSET
                                ? SpellSpec.Shape.SPHERE
                                : s.shape(),
                        s.amount(),
                        s.shape() == SpellSpec.Shape.FAN || s.shape() == SpellSpec.Shape.SHIELD
                                ? 0
                                : s.range() > 0
                                        ? s.range()
                                        : (r.casting().explicitStorage()
                                                ? p.range()
                                                : (int) r.range()),
                        s.method() == SpellSpec.Method.CONTACT || s.shape() == SpellSpec.Shape.BEAM
                                ? 0
                                : s.effects()
                                        .impactRadius(
                                                s.radius() > 0
                                                        ? s.radius()
                                                        : (r.casting().explicitStorage()
                                                                ? p.radius()
                                                                : (int) r.radius())),
                        s.aim() < 0 ? (r.casting().explicitStorage() ? p.aim() : 8192) : s.aim(),
                        s.effects(),
                        s.damageMultiplier(),
                        s.reactive())
                .withMaterials(s.materials())
                .withConnection(s.connection())
                .withSpatial(s.spatial());
    }

    public static Quote quote(SpellSpec spec, Rules rules, CastContext context, ShapeRules shapes) {
        SpellSpec s =
                resolve(spec, rules)
                        .withDamageMultiplier(context.damageMultiplier())
                        .withReactive(spec.reactive() || context.reactive());
        var policy = rules.casting();
        long amount = spec.amount() > 0 ? spec.amount() : policy.defaultEffect();
        long divisor =
                s.method() == SpellSpec.Method.DIRECTED
                        ? Math.multiplyExact(
                                Math.max(1, s.range()), Fixed.ONE + 4 * (Fixed.ONE - s.aim()))
                        : Fixed.ONE;
        // Radius is paid through footprint, not a second volume penalty. Legacy parameter loss is
        // softened 100x.
        long scale = policy.parameterLossScale();
        BigInteger d = BigInteger.valueOf(divisor), f = BigInteger.valueOf(Fixed.ONE);
        BigInteger retentionNumerator =
                d.multiply(f).subtract(BigInteger.valueOf(scale).multiply(d.subtract(f)));
        long footprint = ShapeFootprint.blocks(s, shapes);
        BigInteger base =
                BigInteger.valueOf(amount)
                        .add(
                                BigInteger.valueOf(footprint)
                                        .multiply(BigInteger.valueOf(policy.shapeCostPerBlock())));
        BigInteger numerator =
                base.multiply(BigInteger.valueOf(context.manaMultiplier())).multiply(d);
        long required =
                Math.max(
                        1,
                        numerator
                                .divide(retentionNumerator)
                                .min(BigInteger.valueOf(Long.MAX_VALUE))
                                .longValueExact());
        long payload =
                Math.min(
                        amount,
                        Math.max(
                                1,
                                amount
                                        * Math.min(Fixed.ONE, context.manaMultiplier())
                                        / Fixed.ONE));
        payload = Math.min(payload, required);
        return new Quote(
                s.withEffectScale(new EffectScale(amount, payload)),
                payload,
                required,
                footprint,
                divisor);
    }
}
