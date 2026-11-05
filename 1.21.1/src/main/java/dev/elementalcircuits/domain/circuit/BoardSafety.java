package dev.elementalcircuits.domain.circuit;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.Mixture;

import java.util.*;

/** Pure overload and blast equations. No damage, inventory or rendering capability. */
public record BoardSafety(
        int version,
        Map<Rules.Material, Profile> profiles,
        double fuseMultiplier,
        double inputWear,
        double overloadWear,
        double radiusCoefficient,
        double damageCoefficient,
        double maximumRadius,
        double maximumDamage,
        List<Double> volatility) {
    public record Profile(long capacity, int durability) {
        public Profile {
            if (capacity < Fixed.ONE
                    || capacity > Fixed.MAX
                    || durability < 1
                    || durability > 1000000)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._board_safety.001"));
        }
    }

    public record Result(BoardCondition condition, boolean tripped, boolean meltdown) {}

    public record Blast(double radius, double damage, double energy) {}

    public BoardSafety {
        if (version != 1
                || profiles == null
                || profiles.size() != Rules.Material.values().length
                || volatility == null
                || volatility.size() != Mixture.COMPONENTS)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._board_safety.002"));
        profiles = Map.copyOf(profiles);
        volatility = List.copyOf(volatility);
        for (var m : Rules.Material.values()) Objects.requireNonNull(profiles.get(m));
        for (double n :
                new double[] {
                    fuseMultiplier,
                    inputWear,
                    overloadWear,
                    radiusCoefficient,
                    damageCoefficient,
                    maximumRadius,
                    maximumDamage
                })
            if (!Double.isFinite(n) || n < 0 || n > 1024)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._board_safety.003"));
        if (fuseMultiplier < 2 || maximumRadius < .5 || maximumRadius > 16 || maximumDamage < 1)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._board_safety.004"));
        for (double n : volatility)
            if (!Double.isFinite(n) || n < .05 || n > 8)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._board_safety.005"));
    }

    public BoardCondition fresh(Rules.Material material, long supplied) {
        return new BoardCondition(
                1, profiles.get(material).durability() * Fixed.ONE, 0, false, supplied);
    }

    public Result assess(
            BoardCondition before,
            Rules.Material material,
            long load,
            long supplied,
            boolean creative) {
        return assess(before, material, load, supplied, creative, 1);
    }

    public Result assess(
            BoardCondition before,
            Rules.Material material,
            long load,
            long supplied,
            boolean creative,
            double wearMultiplier) {
        if (!Double.isFinite(wearMultiplier) || wearMultiplier < 0 || wearMultiplier > 1)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._board_safety.006"));
        if (load < 0 || supplied < before.supplyCursor())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._board_safety.007"));
        if (before.broken()) return new Result(before, true, false);
        double capacity = profiles.get(material).capacity();
        boolean melt = load >= capacity * fuseMultiplier;
        long input = supplied - before.supplyCursor();
        double excess = Math.max(0, load / capacity - 1);
        double wear =
                (inputWear * input / (4.0 * Fixed.ONE) + overloadWear * excess * excess / 20)
                        * wearMultiplier;
        long spent =
                creative
                        ? before.spent()
                        : melt
                                ? before.maximum()
                                : before.spent()
                                        + Math.min(
                                                before.maximum() - before.spent(),
                                                Math.max(0, (long) Math.floor(wear * Fixed.ONE)));
        var after = new BoardCondition(1, before.maximum(), spent, !creative && melt, supplied);
        return new Result(after, after.broken(), !creative && melt);
    }

    public Blast blast(Rules.Material material, long load, Mixture signature) {
        double capacity = profiles.get(material).capacity(), threshold = capacity * fuseMultiplier;
        if (load < threshold || signature.empty())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._board_safety.008"));
        double h = 0;
        long[] values = signature.values();
        for (int c = 0; c < Mixture.COMPONENTS; c++)
            h += values[c] / (double) signature.total() * volatility.get(c);
        double e = h * (load / threshold) * (1 + Math.max(0, (load - threshold) / capacity));
        return new Blast(
                Math.min(maximumRadius, radiusCoefficient * Math.cbrt(e)),
                Math.min(maximumDamage, damageCoefficient * Math.sqrt(e)),
                e);
    }

    /** Incoming elemental mana is authoritative. Pure input uses reachable conversion imprints. */
    public static Mixture signature(Blueprint blueprint, Mixture active) {
        long pure = active.values()[0];
        if (pure == 0) return active;
        Set<Integer> reached = new HashSet<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (var r : blueprint.runes())
            if (r.kind() == Rune.Kind.INPUT
                    || r.kind() == Rune.Kind.ABSORB
                    || r.kind() == Rune.Kind.MANA_RECEIVE)
                if (reached.add(r.id())) queue.add(r.id());
        var destinations = new HashMap<Integer, List<Integer>>();
        for (var line : blueprint.routes())
            if (line.payload() == Route.Payload.MANA)
                destinations
                        .computeIfAbsent(line.source(), k -> new ArrayList<>())
                        .add(line.target());
        while (!queue.isEmpty())
            for (int next : destinations.getOrDefault(queue.removeFirst(), List.of()))
                if (reached.add(next)) queue.add(next);
        var elements =
                blueprint.runes().stream()
                        .filter(
                                r ->
                                        reached.contains(r.id())
                                                && r.kind() == Rune.Kind.CONVERT
                                                && r.element() != Element.PURE)
                        .map(Rune::element)
                        .toList();
        if (elements.isEmpty()) return active;
        long[] values = active.values();
        values[0] = 0;
        for (int i = 0; i < elements.size(); i++)
            values[elements.get(i).component(false)] +=
                    pure / elements.size() + (i < pure % elements.size() ? 1 : 0);
        return new Mixture(values);
    }
}
