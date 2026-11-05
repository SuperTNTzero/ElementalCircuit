package dev.elementalcircuits.domain.casting;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.circuit.StandardState;
import dev.elementalcircuits.domain.mana.Mixture;

import java.util.*;

/** Frozen gameplay policy; v0 explicitly preserves legacy execution. */
public record CastingRules(
        int version,
        long parameterLossScale,
        long shapeCostPerBlock,
        long failureLossScale,
        List<Long> refundRates,
        long movingLoss,
        long cacheLoss,
        long defaultEffect,
        StandardState standard) {
    public CastingRules(
            int version,
            long parameterLossScale,
            long shapeCostPerBlock,
            long failureLossScale,
            List<Long> refundRates,
            long movingLoss,
            long cacheLoss,
            long defaultEffect) {
        this(
                version,
                parameterLossScale,
                shapeCostPerBlock,
                failureLossScale,
                refundRates,
                movingLoss,
                cacheLoss,
                defaultEffect,
                StandardState.LEGACY);
    }

    public CastingRules {
        if (version < 0
                || version > 2
                || standard == null
                || version < 2 && standard.version() != 0
                || version == 2 && standard.version() != 1
                || parameterLossScale < 0
                || parameterLossScale > Fixed.ONE
                || shapeCostPerBlock < 0
                || shapeCostPerBlock > Fixed.ONE
                || failureLossScale < 0
                || failureLossScale > 4 * Fixed.ONE
                || movingLoss < 0
                || movingLoss > Fixed.ONE
                || cacheLoss < 0
                || cacheLoss > Fixed.ONE
                || defaultEffect < 1
                || defaultEffect > Fixed.MAX
                || refundRates == null
                || refundRates.size() != 17)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._casting_rules.001"));
        refundRates = List.copyOf(refundRates);
        for (long rate : refundRates)
            if (rate < 0 || rate > Fixed.ONE)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.casting._casting_rules.002"));
    }

    public boolean modern() {
        return version >= 1;
    }

    public boolean explicitStorage() {
        return version >= 2;
    }

    public long retention(long historical) {
        return explicitStorage() ? standard.retention(historical) : historical;
    }

    public static CastingRules legacy() {
        return new CastingRules(
                0, Fixed.ONE, 0, Fixed.ONE, Collections.nCopies(17, 0L), 0, 0, Fixed.ONE);
    }

    public long refundRate(int component) {
        return Math.clamp(
                Fixed.ONE - (Fixed.ONE - refundRates.get(component)) * failureLossScale / Fixed.ONE,
                0,
                Fixed.ONE);
    }

    public Mixture refundable(Mixture mana) {
        var parts = new ArrayList<Mixture.Portion>();
        for (var p : mana.order()) {
            long n =
                    p.origin().refundable()
                            ? p.amount() * refundRate(p.component()) / Fixed.ONE
                            : 0;
            if (n > 0) parts.add(new Mixture.Portion(p.component(), n, p.arrival(), p.origin()));
        }
        return Mixture.ordered(parts);
    }
}
