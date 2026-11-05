package dev.elementalcircuits.application;

import dev.elementalcircuits.domain.survey.SurveyArea;

import java.util.*;
import java.util.concurrent.*;

/** Incremental read-only scan. No executor ownership, world reads or blocking future waits. */
public final class NodeSurvey {
    public record Sample(boolean generated, long nodes) {
        public Sample {
            if (nodes < 0 || nodes > 2097152 || !generated && nodes != 0)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.application._node_survey.001"));
        }
    }

    public interface Reader {
        CompletableFuture<Sample> read(SurveyArea.Chunk chunk);
    }

    public record Progress(
            int total,
            int checked,
            int generated,
            int missing,
            int failed,
            int pending,
            long nodes,
            boolean done,
            boolean incomplete) {}

    private final List<SurveyArea.Chunk> chunks;
    private final List<CompletableFuture<Sample>> pending = new ArrayList<>();
    private int cursor, checked, generated, missing, failed;
    private long nodes;
    private boolean stopped;

    public NodeSurvey(SurveyArea area) {
        chunks = area.chunks();
    }

    private void collect(CompletableFuture<Sample> future) {
        try {
            Sample s = Objects.requireNonNull(future.getNow(null));
            if (s.generated()) {
                generated++;
                nodes += s.nodes();
            } else missing++;
        } catch (RuntimeException error) {
            failed++;
        }
        checked++;
    }

    public int pump(Reader reader, int starts, int readAhead) {
        if (starts < 0 || starts > 1024 || readAhead < 1 || readAhead > 16)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.application._node_survey.002"));
        if (stopped) return 0;
        var iterator = pending.iterator();
        while (iterator.hasNext()) {
            var f = iterator.next();
            if (f.isDone()) {
                iterator.remove();
                collect(f);
            }
        }
        int used = 0;
        while (used < starts && cursor < chunks.size() && pending.size() < readAhead) {
            var chunk = chunks.get(cursor++);
            used++;
            try {
                var future = Objects.requireNonNull(reader.read(chunk));
                if (future.isDone()) collect(future);
                else pending.add(future);
            } catch (RuntimeException error) {
                checked++;
                failed++;
            }
        }
        return used;
    }

    /** Native reads may be shared: discard our references, never cancel the engine's IO future. */
    public void stop() {
        stopped = true;
        pending.clear();
    }

    public Progress progress() {
        boolean done = stopped || checked == chunks.size();
        return new Progress(
                chunks.size(),
                checked,
                generated,
                missing,
                failed,
                pending.size(),
                nodes,
                done,
                stopped || missing > 0 || failed > 0);
    }
}
