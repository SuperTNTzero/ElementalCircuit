package dev.elementalcircuits.application.runtime;

import dev.elementalcircuits.domain.CircuitAssembly;
import java.util.UUID;
import java.util.function.Function;

/** World owns transport state; the scheduler controls its causal phase boundaries. */
public interface CircuitExchange {
    void deliver(Function<UUID,CircuitAssembly> resolve);
    void publish(Function<UUID,CircuitAssembly> resolve);
}
