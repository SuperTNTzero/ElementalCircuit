package dev.elementalcircuits.domain.examples;

import dev.elementalcircuits.domain.Hex;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.HexRouter;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.logic.LogicDefaults;

import java.util.*;

/** A normal editable blueprint, with no extra supply or special execution path. */
public final class LogicExamples {
    private LogicExamples() {}

    public static Blueprint alternating(Rules rules, LogicDefaults defaults) {
        List<Rune> nodes = new ArrayList<>();
        Rune.Kind[] kinds = {
            Rune.Kind.CLOCK,
            Rune.Kind.COUNTER,
            Rune.Kind.TOGGLE,
            Rune.Kind.NOT,
            Rune.Kind.INPUT,
            Rune.Kind.SWITCH,
            Rune.Kind.SWITCH,
            Rune.Kind.CONVERT,
            Rune.Kind.CONVERT,
            Rune.Kind.DIRECTED,
            Rune.Kind.DIRECTED,
            Rune.Kind.BEAM,
            Rune.Kind.BEAM,
            Rune.Kind.OUTPUT,
            Rune.Kind.OUTPUT,
            Rune.Kind.OR
        };
        int[][] positions = {
            {-3, -1}, {-1, -1}, {1, -1}, {2, -2}, {-3, 2}, {-1, 2}, {0, 3}, {0, 1}, {1, 2}, {1, 0},
            {2, 1}, {2, 0}, {3, 0}, {3, -1}, {4, -1}, {0, -2}
        };
        for (int i = 0; i < kinds.length; i++) {
            Rune r =
                    Rune.create(
                            i + 1, kinds[i], new Hex(positions[i][0], positions[i][1]), defaults);
            nodes.add(
                    new Rune(
                            r.id(),
                            r.kind(),
                            r.cell(),
                            i == 8 ? Element.WATER : Element.FIRE,
                            r.value(),
                            r.on(),
                            i == 5 || i == 6,
                            r.weights()));
        }
        Blueprint board = new Blueprint(5, rules, nodes, List.of(), true);
        List<Route> routes = new ArrayList<>();
        int[][] signal = {{1, 2}, {2, 3}, {3, 4}, {4, 6}, {3, 16}, {16, 7}};
        int[][] mana = {
            {5, 6}, {5, 7}, {6, 8}, {8, 10}, {10, 12}, {12, 14}, {7, 9}, {9, 11}, {11, 13}, {13, 15}
        };
        for (int[] e : signal) routes.add(route(board, e, Route.Payload.SIGNAL));
        for (int[] e : mana) routes.add(route(board, e, Route.Payload.MANA));
        return new Blueprint(5, rules, nodes, routes, true);
    }

    private static Route route(Blueprint b, int[] edge, Route.Payload payload) {
        return new Route(
                edge[0],
                edge[1],
                payload,
                HexRouter.segment(b, b.rune(edge[0]).cell(), b.rune(edge[1]).cell(), List.of()));
    }
}
