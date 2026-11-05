package dev.elementalcircuits.application.runtime;

import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.CastContext;
import dev.elementalcircuits.domain.casting.ReleasePermit;
import dev.elementalcircuits.domain.casting.ShapeRules;
import dev.elementalcircuits.domain.circuit.CircuitAssembly;
import dev.elementalcircuits.domain.circuit.CircuitProtection;
import dev.elementalcircuits.domain.mana.ManaSource;

/** Platform supplies capabilities. A host never owns world scheduling or VM evaluation. */
public interface CircuitHost {
    CircuitAssembly assembly();

    /**
     * Verify current equipment/anchor conditions. Disabled hosts are flushed but not resolved by
     * links.
     */
    boolean prepare();

    ManaSource supply();

    /** Sample platform signals after transport delivery, before any circuit evaluates this tick. */
    default void sampleExternalSignals() {}

    default ManaSource ambient() {
        return ManaSource.NONE;
    }

    default CastContext castContext() {
        return CastContext.NEUTRAL;
    }

    default ShapeRules shapeRules() {
        return ShapeRules.protocolV1();
    }

    int steps();

    default CircuitProtection protection(int slot) {
        return CircuitProtection.NONE;
    }

    default ReleasePermit releasePermit() {
        return ReleasePermit.UNLIMITED;
    }

    void emit(CircuitAssembly.Output output);

    void afterAdvance();

    void commit();

    void failed(RuntimeException error);
}
