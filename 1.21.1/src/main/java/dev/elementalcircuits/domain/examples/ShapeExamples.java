package dev.elementalcircuits.domain.examples;

import dev.elementalcircuits.domain.Hex;
import dev.elementalcircuits.domain.casting.ShapeVolume;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.HexRouter;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;

import java.util.*;

/** The editor and contract suite use the same routed, usable starter configurations. */
public final class ShapeExamples {
    private ShapeExamples() {}

    public static Blueprint create(Rules rules, Rune.Kind shape) {
        if (!ShapeVolume.rune(shape)
                && shape != Rune.Kind.SPHERE
                && shape != Rune.Kind.BEAM
                && shape != Rune.Kind.SHIELD)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.examples._shape_examples.001"));
        Rune.Kind method =
                ShapeVolume.rune(shape) && shape != Rune.Kind.FAN && shape != Rune.Kind.WALL
                        ? Rune.Kind.AREA
                        : Rune.Kind.DIRECTED;
        List<Rune> nodes =
                List.of(
                        Rune.create(1, Rune.Kind.INPUT, new Hex(-3, 0)),
                        Rune.create(2, Rune.Kind.CONVERT, new Hex(-1, 0)),
                        Rune.create(3, method, new Hex(0, 1)),
                        Rune.create(4, shape, new Hex(2, 0)),
                        Rune.create(5, Rune.Kind.OUTPUT, new Hex(3, -2)));
        Blueprint board = new Blueprint(5, rules, nodes, List.of(), false);
        List<Route> routes = new ArrayList<>();
        for (int i = 0; i < nodes.size() - 1; i++)
            routes.add(
                    new Route(
                            nodes.get(i).id(),
                            nodes.get(i + 1).id(),
                            Route.Payload.MANA,
                            HexRouter.segment(
                                    board,
                                    nodes.get(i).cell(),
                                    nodes.get(i + 1).cell(),
                                    List.of())));
        return new Blueprint(5, rules, nodes, routes, false);
    }
}
