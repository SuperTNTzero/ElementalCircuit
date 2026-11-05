package dev.elementalcircuits.domain.circuit;

import java.util.*;

/** Iterative O(V+E), derived once per immutable definition. Unused ornaments cost nothing. */
public record CircuitComplexity(
        int nodes, int links, long length, int depth, int feedback, long score) {
    public static CircuitComplexity of(Blueprint b) {
        int n = b.runes().size();
        var ids = new HashMap<Integer, Integer>();
        List<List<Integer>> out = new ArrayList<>(), in = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.put(b.runes().get(i).id(), i);
            out.add(new ArrayList<>());
            in.add(new ArrayList<>());
        }
        for (var e : b.routes()) {
            int s = ids.get(e.source()), t = ids.get(e.target());
            out.get(s).add(t);
            in.get(t).add(s);
        }
        boolean[] forward = new boolean[n], backward = new boolean[n];
        var q = new ArrayDeque<Integer>();
        for (int i = 0; i < n; i++) {
            var k = b.runes().get(i).kind();
            if (k == Rune.Kind.INPUT
                    || k == Rune.Kind.ABSORB
                    || k == Rune.Kind.MANA_RECEIVE
                    || k.signalOut()) {
                forward[i] = true;
                q.add(i);
            }
        }
        visit(q, out, forward);
        for (int i = 0; i < n; i++) {
            var k = b.runes().get(i).kind();
            if (k == Rune.Kind.OUTPUT || k == Rune.Kind.MANA_SEND || k == Rune.Kind.SIGNAL_SEND) {
                backward[i] = true;
                q.add(i);
            }
        }
        visit(q, in, backward);
        boolean[] active = new boolean[n];
        int count = 0, links = 0;
        long length = 0;
        int[] degree = new int[n], depth = new int[n];
        for (int i = 0; i < n; i++) {
            active[i] = forward[i] && backward[i];
            if (active[i]) count++;
        }
        for (var e : b.routes()) {
            int s = ids.get(e.source()), t = ids.get(e.target());
            if (active[s] && active[t]) {
                degree[t]++;
                links++;
                length += e.length();
            }
        }
        for (int i = 0; i < n; i++) if (active[i] && degree[i] == 0) q.add(i);
        int visited = 0, maxDepth = 0;
        while (!q.isEmpty()) {
            int s = q.remove();
            visited++;
            maxDepth = Math.max(maxDepth, depth[s]);
            for (int t : out.get(s))
                if (active[t]) {
                    depth[t] = Math.max(depth[t], depth[s] + 1);
                    if (--degree[t] == 0) q.add(t);
                }
        }
        int feedback = count - visited;
        var p = b.rules().casting().standard();
        long score =
                count * p.nodeWeight()
                        + length * p.lengthWeight()
                        + maxDepth * p.depthWeight()
                        + feedback * p.feedbackWeight();
        return new CircuitComplexity(count, links, length, maxDepth, feedback, score);
    }

    private static void visit(ArrayDeque<Integer> q, List<List<Integer>> edges, boolean[] seen) {
        while (!q.isEmpty())
            for (int t : edges.get(q.remove()))
                if (!seen[t]) {
                    seen[t] = true;
                    q.add(t);
                }
    }
}
