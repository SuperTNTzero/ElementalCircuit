package dev.elementalcircuits.domain.examples;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.Hex;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;

import java.util.*;

/** Reproducible reference and a directly playable accumulator example. */
public final class StandardCircuit {
    private StandardCircuit() {}

    public static Blueprint create(Rules rules, boolean storage) {
        var kinds =
                storage
                        ? List.of(
                                Rune.Kind.INPUT,
                                Rune.Kind.STORAGE,
                                Rune.Kind.CONVERT,
                                Rune.Kind.DIRECTED,
                                Rune.Kind.BEAM,
                                Rune.Kind.OUTPUT)
                        : List.of(
                                Rune.Kind.INPUT,
                                Rune.Kind.CONVERT,
                                Rune.Kind.DIRECTED,
                                Rune.Kind.BEAM,
                                Rune.Kind.OUTPUT);
        var runes = new ArrayList<Rune>();
        var routes = new ArrayList<Route>();
        for (int i = 0; i < kinds.size(); i++) {
            Rune r = Rune.create(i + 1, kinds.get(i), new Hex(i - 2, 0));
            if (storage && i == 0)
                r =
                        new Rune(
                                r.id(),
                                r.kind(),
                                r.cell(),
                                r.element(),
                                Fixed.ONE,
                                r.on(),
                                r.signalControl(),
                                r.weights());
            runes.add(r);
            if (i > 0)
                routes.add(
                        new Route(
                                i,
                                i + 1,
                                Route.Payload.MANA,
                                List.of(runes.get(i - 1).cell(), r.cell())));
        }
        return new Blueprint(5, rules, runes, routes, storage);
    }
}
