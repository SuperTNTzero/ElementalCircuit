package dev.elementalcircuits.domain.combat;

import dev.elementalcircuits.domain.mana.Mixture;

import java.util.*;

/** Linear FIFO reducer. A lot is consumed at most once; no enum-order all-pairs settlement. */
public final class ElementReactionQueue {
    public record Collision(
            int earlierIndex,
            int incomingIndex,
            int earlierComponent,
            int incomingComponent,
            long amount) {
        public boolean steam() {
            return earlierComponent == Element.FIRE.component(false)
                            && incomingComponent == Element.WATER.component(false)
                    || incomingComponent == Element.FIRE.component(false)
                            && earlierComponent == Element.WATER.component(false);
        }
    }

    public record Result(Mixture remaining, Mixture consumed, List<Collision> collisions) {
        public Result {
            collisions = List.copyOf(collisions);
        }
    }

    private ElementReactionQueue() {}

    public static Result reduce(Mixture input, ElementReactionRules rules) {
        var source = input.order();
        if (source.size() < 2) return new Result(input, Mixture.EMPTY, List.of());
        int[] mask = new int[Mixture.COMPONENTS];
        for (var pair : rules.counters()) {
            mask[pair.a()] |= 1 << pair.b();
            mask[pair.b()] |= 1 << pair.a();
        }
        long[] left = new long[source.size()];
        var waiting = new ArrayList<ArrayDeque<Integer>>();
        for (int c = 0; c < Mixture.COMPONENTS; c++) waiting.add(new ArrayDeque<>());
        var events = new ArrayList<Collision>();
        for (int i = 0; i < source.size(); i++) {
            var incoming = source.get(i);
            long amount = incoming.amount();
            while (amount > 0) {
                int oldest = Integer.MAX_VALUE;
                for (int c = 1; c < Mixture.COMPONENTS; c++)
                    if ((mask[incoming.component()] & (1 << c)) != 0 && !waiting.get(c).isEmpty())
                        oldest = Math.min(oldest, waiting.get(c).getFirst());
                if (oldest == Integer.MAX_VALUE) break;
                var earlier = source.get(oldest);
                long consumed = Math.min(amount, left[oldest]);
                left[oldest] -= consumed;
                amount -= consumed;
                events.add(
                        new Collision(
                                oldest, i, earlier.component(), incoming.component(), consumed));
                if (left[oldest] == 0) waiting.get(earlier.component()).removeFirst();
            }
            left[i] = amount;
            if (amount > 0) waiting.get(incoming.component()).addLast(i);
        }
        var kept = new ArrayList<Mixture.Portion>();
        var used = new ArrayList<Mixture.Portion>();
        for (int i = 0; i < source.size(); i++) {
            var p = source.get(i);
            if (left[i] > 0)
                kept.add(new Mixture.Portion(p.component(), left[i], p.arrival(), p.origin()));
            if (left[i] < p.amount())
                used.add(
                        new Mixture.Portion(
                                p.component(), p.amount() - left[i], p.arrival(), p.origin()));
        }
        return new Result(Mixture.ordered(kept), Mixture.ordered(used), events);
    }
}
