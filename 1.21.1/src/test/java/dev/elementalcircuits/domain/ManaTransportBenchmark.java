package dev.elementalcircuits.domain;

import dev.elementalcircuits.domain.mana.Mixture;

import java.lang.management.ManagementFactory;
import java.util.Arrays;

/** Opt-in warmed CPU/allocation sample; no timing assertions or effects in the real VM. */
public final class ManaTransportBenchmark {
    private static volatile long sink;
    private static final com.sun.management.ThreadMXBean MEMORY =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final int ITERATIONS = 400, LENGTH = 1024;

    private static Mixture route(Mixture m, boolean optimized) {
        if (optimized) return m.transmit(65000, 65405, LENGTH);
        Mixture result = m.retain(65000);
        for (int step = 0; step < LENGTH; step++) result = result.retain(65405);
        return result.retain(65000);
    }

    private static long[] sample(boolean optimized) {
        long id = Thread.currentThread().threadId(),
                bytes = MEMORY.getThreadAllocatedBytes(id),
                start = System.nanoTime(),
                sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            long[] values = new long[17];
            for (int c = 0; c < 17; c++) values[c] = 10000000L + i + c;
            sum += route(new Mixture(values), optimized).total();
        }
        long nanos = System.nanoTime() - start;
        bytes = MEMORY.getThreadAllocatedBytes(id) - bytes;
        sink = sum;
        return new long[] {nanos, bytes, sum};
    }

    public static void main(String[] args) {
        MEMORY.setThreadAllocatedMemoryEnabled(true);
        for (int i = 0; i < 4; i++) {
            sample(false);
            sample(true);
        }
        long[][] old = new long[7][], next = new long[7][];
        for (int i = 0; i < 7; i++) {
            if (i % 2 == 0) {
                old[i] = sample(false);
                next[i] = sample(true);
            } else {
                next[i] = sample(true);
                old[i] = sample(false);
            }
            if (old[i][2] != next[i][2]) throw new AssertionError("基准损耗结果不同");
        }
        for (String name : new String[] {"legacy", "optimized"}) {
            long[][] data = name.equals("legacy") ? old : next;
            long[] times = Arrays.stream(data).mapToLong(v -> v[0]).sorted().toArray();
            long[] bytes = Arrays.stream(data).mapToLong(v -> v[1]).sorted().toArray();
            System.out.printf(
                    java.util.Locale.ROOT,
                    "%s,%d,%d,%.3f,%d,%d%n",
                    name,
                    ITERATIONS,
                    LENGTH,
                    times[3] / 1e6,
                    bytes[3],
                    sink);
        }
    }
}
