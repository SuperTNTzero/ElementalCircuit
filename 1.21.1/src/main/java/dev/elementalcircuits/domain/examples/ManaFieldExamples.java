package dev.elementalcircuits.domain.examples;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.Hex;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;

import java.util.*;

public final class ManaFieldExamples {
    private ManaFieldExamples() {}

    public static Blueprint beam(Rules rules) {
        List<Rune> runes = new ArrayList<>();
        Rune.Kind[] kinds = {
            Rune.Kind.ABSORB,
            Rune.Kind.CONVERT,
            Rune.Kind.DIRECTED,
            Rune.Kind.BEAM,
            Rune.Kind.AMOUNT,
            Rune.Kind.OUTPUT
        };
        for (int i = 0; i < kinds.length; i++) {
            var rune = Rune.create(i + 1, kinds[i], new Hex(i - 2, 0));
            if (kinds[i] == Rune.Kind.AMOUNT)
                rune =
                        new Rune(
                                rune.id(),
                                rune.kind(),
                                rune.cell(),
                                rune.element(),
                                Fixed.ONE / 16,
                                rune.on(),
                                rune.signalControl(),
                                rune.weights());
            runes.add(rune);
        }
        List<Route> routes = new ArrayList<>();
        for (int i = 0; i < kinds.length - 1; i++)
            routes.add(
                    new Route(
                            i + 1,
                            i + 2,
                            Route.Payload.MANA,
                            List.of(runes.get(i).cell(), runes.get(i + 1).cell())));
        return new Blueprint(5, rules, runes, routes, true);
    }
}
