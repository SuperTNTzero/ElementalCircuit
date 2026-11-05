package dev.elementalcircuits.application.runtime;

import dev.elementalcircuits.domain.circuit.CircuitAssembly;

import java.util.*;
import java.util.function.Consumer;

/** Engine-free stable world step. Host capabilities isolate storage and effects from evaluation. */
public final class CircuitScheduler {
    private record Entry(CircuitHost host, CircuitAssembly assembly) {}

    public void tick(Collection<? extends CircuitHost> hosts, CircuitExchange exchange) {
        NavigableMap<UUID, Entry> ready = new TreeMap<>();
        List<Entry> loaded = new ArrayList<>();
        Set<UUID> duplicates = new HashSet<>();
        for (CircuitHost host : hosts) {
            try {
                CircuitAssembly assembly = host.assembly();
                Entry current = new Entry(host, assembly);
                loaded.add(current);
                Entry previous = ready.putIfAbsent(assembly.identity(), current);
                if (previous != null) {
                    duplicates.add(assembly.identity());
                    fail(
                            previous,
                            new IllegalArgumentException(
                                    dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.application.runtime._circuit_scheduler.001")));
                    fail(
                            current,
                            new IllegalArgumentException(
                                    dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.application.runtime._circuit_scheduler.001")));
                }
            } catch (RuntimeException error) {
                report(host, error);
            }
        }
        duplicates.forEach(ready::remove);
        // Disabled equipment must still flush its stopped state at the boundary.
        phase(
                ready,
                entry -> {
                    if (!entry.host.prepare()) ready.remove(entry.assembly.identity());
                });
        phase(ready, entry -> entry.assembly.clearDrives());
        exchange.deliver(id -> resolve(ready, id));
        phase(ready, entry -> entry.assembly.deliver());
        phase(ready, entry -> entry.host.sampleExternalSignals());
        phase(
                ready,
                entry -> {
                    if (entry.assembly.active()) {
                        for (CircuitAssembly.Output output :
                                entry.assembly.advance(
                                        entry.host.supply(),
                                        entry.host.steps(),
                                        entry.host.releasePermit(),
                                        entry.host.ambient(),
                                        entry.host.castContext(),
                                        entry.host.shapeRules(),
                                        entry.host::protection)) {
                            entry.host.emit(output);
                        }
                        entry.host.afterAdvance();
                    }
                });
        phase(ready, entry -> entry.assembly.publish());
        exchange.publish(id -> resolve(ready, id));
        for (Entry entry : loaded) {
            try {
                entry.host.commit();
            } catch (RuntimeException error) {
                fail(entry, error);
            }
        }
    }

    private void report(CircuitHost host, RuntimeException error) {
        try {
            host.failed(error);
        } catch (RuntimeException reportingFailure) {
            /* Reporting is not an execution capability. */
        }
    }

    private void fail(Entry entry, RuntimeException error) {
        entry.assembly.stopAll();
        report(entry.host, error);
    }

    private CircuitAssembly resolve(Map<UUID, Entry> entries, UUID identity) {
        Entry entry = entries.get(identity);
        return entry == null ? null : entry.assembly;
    }

    private void phase(NavigableMap<UUID, Entry> entries, Consumer<Entry> operation) {
        for (Entry entry : List.copyOf(entries.values())) {
            try {
                operation.accept(entry);
            } catch (RuntimeException error) {
                entries.remove(entry.assembly.identity());
                fail(entry, error);
            }
        }
    }
}
