package dev.elementalcircuits.domain.circuit;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.Hex;

import java.util.*;

/** Configuration only. Route ordering is semantic, never sorted by map iteration. */
public record Blueprint(
        int version, Rules rules, List<Rune> runes, List<Route> routes, boolean continuous) {
    public Blueprint {
        if (version != 5
                || rules == null
                || runes == null
                || routes == null
                || runes.size() > CircuitBounds.RUNES
                || routes.size() > CircuitBounds.ROUTES)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._blueprint.001"));
        runes = List.copyOf(runes);
        routes = List.copyOf(routes);
        Map<Integer, Rune> byId = new HashMap<>();
        Set<Hex> occupied = new HashSet<>();
        for (Rune rune : runes) {
            if (rune == null
                    || !rune.cell().inside(rules.boardRadius())
                    || byId.put(rune.id(), rune) != null
                    || !occupied.add(rune.cell()))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._blueprint.002"));
            if ((rune.kind() == Rune.Kind.INPUT || rune.kind() == Rune.Kind.ABSORB)
                    && rune.value() != 0
                    && (rune.value() < 1 || rune.value() > Fixed.MAX))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._blueprint.003"));
        }
        if (runes.stream().anyMatch(r -> r.kind() == Rune.Kind.STORAGE)
                && !rules.casting().explicitStorage())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._blueprint.004"));
        Set<String> duplicates = new HashSet<>();
        // Count split weights in the original route order while validating each edge. Scanning
        // all routes again for every splitter made large editor drafts quadratic.
        Map<Integer, Integer> splitCounts = new HashMap<>();
        Set<Integer> positiveSplits = new HashSet<>();
        for (Route route : routes) {
            Rune from = byId.get(route.source()), to = byId.get(route.target());
            if (from == null
                    || to == null
                    || !route.path().get(0).equals(from.cell())
                    || !route.path().get(route.path().size() - 1).equals(to.cell()))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._blueprint.005"));
            boolean mana = route.payload() == Route.Payload.MANA;
            if (route.sourcePort() >= from.kind().outputPorts(route.payload()))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._blueprint.006"));
            if (mana
                    ? !from.kind().manaOut() || !to.kind().manaIn()
                    : !from.kind().signalOut() || !to.kind().signalIn())
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._blueprint.007"));
            if (!duplicates.add(
                    route.source()
                            + ":"
                            + route.target()
                            + ":"
                            + route.payload()
                            + ":"
                            + route.sourcePort()))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._blueprint.008"));
            if (mana && from.kind() == Rune.Kind.SPLIT) {
                int count = splitCounts.getOrDefault(from.id(), 0);
                int weight = count < from.weights().size() ? from.weights().get(count) : 1;
                if (weight > 0) positiveSplits.add(from.id());
                splitCounts.put(from.id(), count + 1);
            }
            Set<Hex> seen = new HashSet<>();
            for (int i = 0; i < route.path().size(); i++) {
                Hex cell = route.path().get(i);
                if (!cell.inside(rules.boardRadius())
                        || !seen.add(cell)
                        || (i > 0 && cell.distance(route.path().get(i - 1)) != 1)
                        || (i > 0 && i < route.path().size() - 1 && occupied.contains(cell)))
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.domain.circuit._blueprint.009"));
            }
        }
        for (int split : splitCounts.keySet())
            if (!positiveSplits.contains(split))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._blueprint.010"));
    }

    public static Blueprint empty(Rules rules) {
        return new Blueprint(5, rules, List.of(), List.of(), false);
    }

    public Rune rune(int id) {
        return runes.stream().filter(r -> r.id() == id).findFirst().orElseThrow();
    }

    public long inputAmount(Rune rune) {
        if (rune.kind() != Rune.Kind.INPUT && rune.kind() != Rune.Kind.ABSORB)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._blueprint.011"));
        return rune.value() == 0 ? rules.injection() : rune.value();
    }

    public Blueprint withInputAmount(int id, long amount) {
        Rune selected = rune(id);
        if (selected.kind() != Rune.Kind.INPUT && selected.kind() != Rune.Kind.ABSORB)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._blueprint.011"));
        if (amount < 1 || amount > Fixed.MAX)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._blueprint.003"));
        var updated =
                runes.stream()
                        .map(
                                r -> {
                                    long value = r.id() == id ? amount : r.value();
                                    return new Rune(
                                            r.id(),
                                            r.kind(),
                                            r.cell(),
                                            r.element(),
                                            value,
                                            r.on(),
                                            r.signalControl(),
                                            r.weights(),
                                            r.storage(),
                                            r.filter());
                                })
                        .toList();
        return new Blueprint(version, rules, updated, routes, continuous);
    }

    public Blueprint withMaterial(Rules.Material m) {
        return new Blueprint(version, rules.withMaterial(m), runes, routes, continuous);
    }
}
