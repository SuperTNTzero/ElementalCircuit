package dev.elementalcircuits.domain;

import java.util.*;

/** Configuration only. Route ordering is semantic, never sorted by map iteration. */
public record Blueprint(int version, Rules rules, List<Rune> runes, List<Route> routes, boolean continuous) {
    public Blueprint {
        if (version != 1 || rules == null || runes == null || routes == null || runes.size() > 64 || routes.size() > 64)
            throw new IllegalArgumentException("蓝图版本或大小不合法");
        runes = List.copyOf(runes); routes = List.copyOf(routes);
        Map<Integer, Rune> byId = new HashMap<>(); Set<Hex> occupied = new HashSet<>();
        for (Rune rune : runes) {
            if (rune == null || !rune.cell().inside(rules.boardRadius()) || byId.put(rune.id(), rune) != null || !occupied.add(rune.cell()))
                throw new IllegalArgumentException("符文重叠、重复或越界");
        }
        Set<String> duplicates = new HashSet<>();
        for (Route route : routes) {
            Rune from = byId.get(route.source()), to = byId.get(route.target());
            if (from == null || to == null || !route.path().get(0).equals(from.cell()) || !route.path().get(route.path().size()-1).equals(to.cell()))
                throw new IllegalArgumentException("线路端点错误");
            boolean mana = route.payload() == Route.Payload.MANA;
            if (mana ? !from.kind().manaOut() || !to.kind().manaIn() : !from.kind().signalOut() || !to.kind().signalIn())
                throw new IllegalArgumentException("符文不支持该载荷");
            if (!duplicates.add(route.source()+":"+route.target()+":"+route.payload())) throw new IllegalArgumentException("重复连线");
            Set<Hex> seen = new HashSet<>();
            for (int i = 0; i < route.path().size(); i++) {
                Hex cell = route.path().get(i);
                if (!cell.inside(rules.boardRadius()) || !seen.add(cell) || (i > 0 && cell.distance(route.path().get(i-1)) != 1)
                    || (i > 0 && i < route.path().size()-1 && occupied.contains(cell))) throw new IllegalArgumentException("路线越界、自交、跳格或穿符");
            }
        }
        for (Rune rune : runes) if (rune.kind() == Rune.Kind.SPLIT) {
            int count = 0; boolean positive = false;
            for (Route route : routes) if (route.source() == rune.id() && route.payload() == Route.Payload.MANA) {
                int weight = count < rune.weights().size() ? rune.weights().get(count) : 1;
                positive |= weight > 0; count++;
            }
            if (count > 0 && !positive) throw new IllegalArgumentException("分流出口不能全部为零");
        }
    }
    public static Blueprint empty(Rules rules) { return new Blueprint(1, rules, List.of(), List.of(), false); }
    public Rune rune(int id) { return runes.stream().filter(r -> r.id() == id).findFirst().orElseThrow(); }
    public Blueprint withMaterial(Rules.Material m) { return new Blueprint(version, rules.withMaterial(m), runes, routes, continuous); }
}
