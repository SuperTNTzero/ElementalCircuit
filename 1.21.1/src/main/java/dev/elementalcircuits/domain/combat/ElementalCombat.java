package dev.elementalcircuits.domain.combat;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.mana.ManaOrigin;
import dev.elementalcircuits.domain.mana.Mixture;

import java.util.*;

/** Pure one-contact evaluation. Neither enum iteration nor rendering controls reaction order. */
public final class ElementalCombat {
    public enum Reaction {
        STEAM,
        CONDUCTION,
        FREEZE,
        SHATTER,
        MUD,
        FAN,
        PURIFY,
        NEUTRALIZE
    }

    public enum WetChange {
        KEEP,
        APPLY,
        CLEAR
    }

    public record State(
            boolean wet,
            boolean burning,
            int frozenTicks,
            boolean fireImmune,
            boolean freezeImmune,
            boolean undead,
            boolean environmentWet,
            boolean frostSensitive) {
        public State(
                boolean wet,
                boolean burning,
                int frozenTicks,
                boolean fireImmune,
                boolean freezeImmune,
                boolean undead) {
            this(wet, burning, frozenTicks, fireImmune, freezeImmune, undead, false, false);
        }

        public State {
            if (frozenTicks < 0 || frozenTicks > Integer.MAX_VALUE - 1)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.combat._elemental_combat.001"));
        }
    }

    public record Status(
            WetChange wet,
            int igniteSeconds,
            int fanTicks,
            boolean extinguish,
            int frozenTicks,
            int slowTicks,
            int slowAmplifier,
            int levitationTicks,
            int blindTicks,
            boolean purify,
            long impulse) {
        public Status {
            Objects.requireNonNull(wet);
            for (int v :
                    new int[] {igniteSeconds, fanTicks, slowTicks, levitationTicks, blindTicks})
                if (v < 0 || v > 12000)
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.domain.combat._elemental_combat.002"));
            if (frozenTicks < 0
                    || frozenTicks > Integer.MAX_VALUE - 1
                    || slowAmplifier < 0
                    || slowAmplifier > 1
                    || impulse < -2 * Fixed.ONE
                    || impulse > 2 * Fixed.ONE)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.combat._elemental_combat.003"));
        }
    }

    public record Plan(
            Map<DamageAffinity, Long> damage,
            long healing,
            Status status,
            Set<Reaction> reactions,
            List<Status> steps,
            List<ElementReactionQueue.Collision> queueReactions) {
        public Plan(
                Map<DamageAffinity, Long> damage,
                long healing,
                Status status,
                Set<Reaction> reactions) {
            this(damage, healing, status, reactions, List.of(status), List.of());
        }

        public Plan {
            steps = List.copyOf(steps);
            queueReactions = List.copyOf(queueReactions);
            damage = Map.copyOf(damage);
            reactions = Set.copyOf(reactions);
            if (healing < 0 || healing > 1024 * Fixed.ONE || status == null)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.combat._elemental_combat.004"));
            for (long v : damage.values())
                if (v <= 0 || v > 1024 * Fixed.ONE)
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.domain.combat._elemental_combat.005"));
            if (damage.containsKey(DamageAffinity.PURE)
                    || damage.containsKey(DamageAffinity.MIXED)
                    || damage.values().stream().mapToLong(Long::longValue).sum() > 1024 * Fixed.ONE)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.combat._elemental_combat.006"));
        }

        public long totalDamage() {
            return damage.values().stream().mapToLong(Long::longValue).sum();
        }

        public DamageAffinity affinity() {
            return damage.isEmpty()
                    ? DamageAffinity.PURE
                    : damage.size() == 1 ? damage.keySet().iterator().next() : DamageAffinity.MIXED;
        }

        public Plan extraFrost(long maximum) {
            if (maximum < Fixed.ONE || maximum > 1024 * Fixed.ONE)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.combat._elemental_combat.007"));
            var m = new EnumMap<DamageAffinity, Long>(DamageAffinity.class);
            m.putAll(damage);
            m.computeIfPresent(DamageAffinity.FROST, (k, v) -> v * 5);
            return new Plan(limit(m, maximum), healing, status, reactions, steps, queueReactions);
        }
    }

    private ElementalCombat() {}

    private static long scale(long value, long multiplier) {
        return value * multiplier / Fixed.ONE;
    }

    private static int duration(long value, int perMana, int maximum) {
        return (int) Math.min(maximum, value * perMana / Fixed.ONE);
    }

    private static Map<DamageAffinity, Long> limit(Map<DamageAffinity, Long> raw, long cap) {
        var out = new EnumMap<DamageAffinity, Long>(DamageAffinity.class);
        long total = raw.values().stream().mapToLong(Long::longValue).sum();
        for (var a : DamageAffinity.values()) {
            long value = raw.getOrDefault(a, 0L);
            if (value > 0) {
                long n =
                        total > cap
                                ? java.math.BigInteger.valueOf(value)
                                        .multiply(java.math.BigInteger.valueOf(cap))
                                        .divide(java.math.BigInteger.valueOf(total))
                                        .longValueExact()
                                : value;
                if (n > 0) out.put(a, n);
            }
        }
        return out;
    }

    public static Plan evaluate(
            Mixture mixture, long power, State before, ElementalCombatRules rules) {
        return evaluate(mixture, power, before, rules, Fixed.ONE);
    }

    public static Plan evaluate(
            Mixture mixture,
            long power,
            State before,
            ElementalCombatRules rules,
            long damageMultiplier) {
        if (damageMultiplier < Fixed.ONE || damageMultiplier > 16 * Fixed.ONE)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.combat._elemental_combat.008"));
        if (power < Fixed.ONE / 4 || power > 2 * Fixed.ONE)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.combat._elemental_combat.009"));
        var reduced = ElementReactionQueue.reduce(mixture, rules.reactions());
        var damage = new EnumMap<DamageAffinity, Long>(DamageAffinity.class);
        var steps = new ArrayList<Status>();
        var events = EnumSet.noneOf(Reaction.class);
        for (var c : reduced.collisions())
            events.add(c.steam() ? Reaction.STEAM : Reaction.NEUTRALIZE);
        State current = before;
        long damageBudget = rules.maxDamage(), healing = 0, impulse = 0;
        WetChange wet = WetChange.KEEP;
        int ignite = 0, fan = 0, slow = 0, amp = 0, lift = 0, blind = 0;
        boolean extinguish = false, purify = false;
        for (var portion : effectRuns(reduced.remaining())) {
            var one = atomic(Mixture.ordered(List.of(portion)), power, current, rules);
            var status = one.status();
            steps.add(status);
            events.addAll(one.reactions());
            for (var entry : one.damage().entrySet()) {
                long n = Math.min(damageBudget, scale(entry.getValue(), damageMultiplier));
                if (n > 0) {
                    damage.merge(entry.getKey(), n, Long::sum);
                    damageBudget -= n;
                }
            }
            healing = Math.min(rules.maxDamage(), healing + one.healing());
            boolean isWet = current.wet(), isBurning = current.burning();
            if (status.wet() != WetChange.KEEP) {
                wet = status.wet();
                isWet = status.wet() == WetChange.APPLY || current.environmentWet();
            }
            if (status.extinguish()) {
                isBurning = false;
                extinguish = true;
                ignite = 0;
                fan = 0;
            }
            if (status.igniteSeconds() > 0) {
                isBurning = true;
                ignite = Math.max(ignite, status.igniteSeconds());
            }
            fan = Math.min(12000, fan + status.fanTicks());
            if (status.slowTicks() > 0) {
                if (status.slowAmplifier() > amp) {
                    amp = status.slowAmplifier();
                    slow = status.slowTicks();
                } else if (status.slowAmplifier() == amp) slow = Math.max(slow, status.slowTicks());
            }
            lift = Math.max(lift, status.levitationTicks());
            if (status.purify()) {
                purify = true;
                blind = 0;
            }
            if (status.blindTicks() > 0) blind = status.blindTicks();
            impulse =
                    Math.clamp(
                            impulse + status.impulse(),
                            -rules.status().maxImpulse(),
                            rules.status().maxImpulse());
            current =
                    new State(
                            isWet,
                            isBurning,
                            status.frozenTicks(),
                            before.fireImmune(),
                            before.freezeImmune(),
                            before.undead(),
                            before.environmentWet(),
                            before.frostSensitive());
        }
        var summary =
                new Status(
                        wet,
                        ignite,
                        fan,
                        extinguish,
                        current.frozenTicks(),
                        slow,
                        amp,
                        lift,
                        blind,
                        purify,
                        impulse);
        return new Plan(damage, healing, summary, events, steps, reduced.collisions());
    }

    /**
     * Receiving stamps are transport metadata; adjacent identical effects are one continuous run.
     */
    private static List<Mixture.Portion> effectRuns(Mixture mana) {
        var runs = new ArrayList<Mixture.Portion>();
        for (var p : mana.order()) {
            if (!runs.isEmpty() && runs.getLast().component() == p.component()) {
                var last = runs.removeLast();
                runs.add(
                        new Mixture.Portion(
                                p.component(),
                                last.amount() + p.amount(),
                                last.arrival(),
                                ManaOrigin.UNKNOWN));
            } else runs.add(p);
        }
        return runs;
    }

    private static Plan atomic(
            Mixture mixture, long power, State before, ElementalCombatRules rules) {
        if (power < Fixed.ONE / 4 || power > 2 * Fixed.ONE)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.combat._elemental_combat.009"));
        var s = rules.status();
        var f = rules.multipliers();
        long threshold = rules.threshold();
        long[] a = mixture.values();
        for (int i = 0; i < a.length; i++) a[i] = scale(a[i], power);
        EnumSet<Reaction> reactions = EnumSet.noneOf(Reaction.class);
        long water = a[Element.WATER.component(false)],
                dry = a[Element.WATER.component(true)],
                fire = a[Element.FIRE.component(false)],
                cold = a[Element.FIRE.component(true)];
        boolean wet = (before.wet() || water >= threshold) && dry < threshold;
        WetChange wetChange =
                water >= threshold
                        ? WetChange.APPLY
                        : dry >= threshold ? WetChange.CLEAR : WetChange.KEEP;
        int frozen = before.frozenTicks();
        boolean steam = fire >= threshold && wet;
        if (steam) {
            wetChange = WetChange.CLEAR;
            reactions.add(Reaction.STEAM);
        }
        if (fire >= threshold)
            frozen = Math.max(0, frozen - duration(fire, s.thawPerMana(), Integer.MAX_VALUE - 1));
        boolean shatter =
                frozen >= s.freezeThreshold()
                        && before.frozenTicks() >= s.freezeThreshold()
                        && a[Element.EARTH.component(false)] >= threshold;
        int freezeGain =
                before.freezeImmune()
                        ? 0
                        : duration(
                                wet ? scale(cold, f.wetFrost()) : cold,
                                s.freezePerMana(),
                                s.maxFrozen());
        if (freezeGain > 0) {
            frozen = Math.max(frozen, (int) Math.min(s.maxFrozen(), (long) frozen + freezeGain));
            reactions.add(Reaction.FREEZE);
        }
        if (shatter) {
            frozen = Math.max(0, frozen - s.thawPerMana());
            reactions.add(Reaction.SHATTER);
        }
        var damage = new EnumMap<DamageAffinity, Long>(DamageAffinity.class);
        long healing = 0;
        for (Element e : Element.values())
            if (e != Element.PURE)
                for (int phase = 0; phase < 2; phase++) {
                    boolean inverse = phase == 1;
                    long amount = a[e.component(inverse)];
                    if (amount == 0) continue;
                    if (e == Element.ARCANE && inverse && rules.reactions().version() >= 2)
                        continue;
                    var kind = DamageAffinity.of(e, inverse);
                    long dealt = rules.damage(kind, amount);
                    if (e == Element.LIGHT && !inverse) {
                        if (before.undead()) dealt = scale(dealt, f.undeadLight());
                        else {
                            healing += dealt;
                            continue;
                        }
                    }
                    if (kind == DamageAffinity.FIRE) {
                        if (before.fireImmune()) continue;
                        if (wet) dealt = scale(dealt, f.wetFire());
                    }
                    if (kind == DamageAffinity.FROST) {
                        if (before.freezeImmune()) continue;
                        if (before.frostSensitive()) dealt *= 5;
                    }
                    if (e == Element.LIGHTNING && wet) {
                        dealt = scale(dealt, f.wetLightning());
                        reactions.add(Reaction.CONDUCTION);
                    }
                    if (e == Element.EARTH && !inverse && shatter)
                        dealt = scale(dealt, f.shatter());
                    if (dealt > 0) damage.merge(kind, dealt, Long::sum);
                }
        int slow = water >= threshold ? duration(water, s.slowPerMana(), s.slowMaxTicks()) : 0,
                amp = 0,
                levitate = 0,
                blind = 0;
        long earth = a[Element.EARTH.component(false)], lift = a[Element.EARTH.component(true)];
        if (earth >= threshold) {
            slow = Math.max(slow, duration(earth, s.earthSlowPerMana(), s.earthSlowMaxTicks()));
            if (wet) {
                amp = 1;
                reactions.add(Reaction.MUD);
            }
        }
        if (lift >= threshold)
            levitate = duration(lift, s.earthSlowPerMana(), s.earthSlowMaxTicks());
        if (a[Element.SHADOW.component(false)] >= threshold)
            blind =
                    duration(
                            a[Element.SHADOW.component(false)],
                            s.blindPerMana(),
                            s.blindMaxTicks());
        boolean purify =
                a[Element.LIGHT.component(false)] >= threshold
                        || a[Element.SHADOW.component(true)] >= threshold;
        if (purify) {
            blind = 0;
            reactions.add(Reaction.PURIFY);
        }
        int ignite =
                fire >= threshold && !wet && !before.fireImmune()
                        ? (int) Math.min(s.fireMaxSeconds(), Math.max(1, fire / Fixed.ONE))
                        : 0;
        long wind = a[Element.WIND.component(false)] - a[Element.WIND.component(true)];
        long impulse = Math.clamp(scale(wind, s.impulsePerMana()), -s.maxImpulse(), s.maxImpulse());
        boolean extinguish = water >= threshold || cold >= threshold || steam;
        int fan = wind >= threshold && before.burning() && !extinguish ? s.fanTicks() : 0;
        if (fan > 0) reactions.add(Reaction.FAN);
        return new Plan(
                limit(damage, rules.maxDamage()),
                Math.min(rules.maxDamage(), healing),
                new Status(
                        wetChange,
                        ignite,
                        fan,
                        extinguish,
                        frozen,
                        slow,
                        amp,
                        levitate,
                        blind,
                        purify,
                        impulse),
                reactions);
    }
}
