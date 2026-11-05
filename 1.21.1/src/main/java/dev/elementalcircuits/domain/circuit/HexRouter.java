package dev.elementalcircuits.domain.circuit;

import dev.elementalcircuits.domain.Hex;

import java.util.*;

/** Deterministic shortest grid path. Other runes block cells; crossing existing wires is legal. */
public final class HexRouter {
    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {-1, 1}, {-1, 0}, {0, -1}, {1, -1}};

    private HexRouter() {}

    public static List<Hex> segment(Blueprint board, Hex from, Hex to, Collection<Hex> previous) {
        if (!from.inside(board.rules().boardRadius()) || !to.inside(board.rules().boardRadius()))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._hex_router.001"));
        if (from.equals(to)) return List.of(from);
        Set<Hex> blocked = new HashSet<>(previous);
        board.runes().forEach(r -> blocked.add(r.cell()));
        blocked.remove(from);
        blocked.remove(to);
        if (previous.contains(to))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._hex_router.002"));
        Map<Hex, Hex> parent = new HashMap<>();
        Deque<Hex> open = new ArrayDeque<>();
        open.add(from);
        parent.put(from, from);
        while (!open.isEmpty()) {
            Hex cell = open.removeFirst();
            List<Hex> neighbors = new ArrayList<>();
            for (int[] d : DIRECTIONS) {
                Hex next = new Hex(cell.q() + d[0], cell.r() + d[1]);
                if (next.inside(board.rules().boardRadius()) && !blocked.contains(next))
                    neighbors.add(next);
            }
            neighbors.sort(
                    Comparator.comparingInt((Hex h) -> h.distance(to))
                            .thenComparingInt(Hex::q)
                            .thenComparingInt(Hex::r));
            for (Hex next : neighbors)
                if (!parent.containsKey(next)) {
                    parent.put(next, cell);
                    if (next.equals(to)) {
                        List<Hex> result = new ArrayList<>();
                        for (Hex at = to; !at.equals(from); at = parent.get(at)) result.add(at);
                        result.add(from);
                        Collections.reverse(result);
                        return List.copyOf(result);
                    }
                    open.addLast(next);
                }
        }
        throw new IllegalArgumentException(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._hex_router.003"));
    }
}
