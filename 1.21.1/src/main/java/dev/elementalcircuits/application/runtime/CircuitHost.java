package dev.elementalcircuits.application.runtime;

import dev.elementalcircuits.domain.*;

/** Platform supplies capabilities. A host never owns world scheduling or VM evaluation. */
public interface CircuitHost {
    CircuitAssembly assembly();
    /** Verify current equipment/anchor conditions. Disabled hosts are flushed but not resolved by links. */
    boolean prepare();
    ManaSource supply();
    default ManaSource ambient(){return ManaSource.NONE;}
    int steps();
    default ReleasePermit releasePermit(){return ReleasePermit.UNLIMITED;}
    void emit(CircuitAssembly.Output output);
    void afterAdvance();
    void commit();
    void failed(RuntimeException error);
}
