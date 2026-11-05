package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.resources.ElementalCombatResources;
import dev.elementalcircuits.domain.combat.DamageAffinity;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.combat.ElementalCombat;
import dev.elementalcircuits.domain.combat.ElementalCombatRules;
import dev.elementalcircuits.domain.mana.Mixture;

import java.util.*;

public final class ElementalCombatContracts {
    private static int checks;
    private static final ElementalCombatRules R = ElementalCombatResources.rules();
    private static final ElementalCombat.State
            DRY = new ElementalCombat.State(false, false, 0, false, false, false),
            WET = new ElementalCombat.State(true, false, 0, false, false, false);

    private static void check(boolean ok, String text) {
        checks++;
        if (!ok) throw new AssertionError(text);
    }

    private static Mixture m(Element e, boolean inverse, long amount) {
        long[] a = new long[17];
        a[e.component(inverse)] = amount;
        return new Mixture(a);
    }

    private static ElementalCombat.Plan hit(Mixture m, ElementalCombat.State s) {
        return ElementalCombat.evaluate(m, Fixed.ONE, s, R);
    }

    private static long damage(ElementalCombat.Plan p, DamageAffinity e) {
        return p.damage().getOrDefault(e, 0L);
    }

    public static int run() {
        var fire = m(Element.FIRE, false, Fixed.ONE);
        var water = m(Element.WATER, false, Fixed.ONE);
        var cold = m(Element.FIRE, true, Fixed.ONE);
        var lightning = m(Element.LIGHTNING, false, Fixed.ONE);
        var earth = m(Element.EARTH, false, Fixed.ONE);
        var light = m(Element.LIGHT, false, Fixed.ONE);
        var dark = m(Element.SHADOW, false, Fixed.ONE);
        var wind = m(Element.WIND, false, Fixed.ONE);
        check(damage(hit(fire, DRY), DamageAffinity.FIRE) == 2 * Fixed.ONE, "火基础2");
        check(
                damage(hit(fire, WET), DamageAffinity.FIRE) == Fixed.ONE
                        && hit(fire, WET).status().igniteSeconds() == 0
                        && hit(fire, WET).status().wet() == ElementalCombat.WetChange.CLEAR,
                "潮湿抗火、蒸发，不再点燃");
        check(damage(hit(lightning, WET), DamageAffinity.LIGHTNING) == 3 * Fixed.ONE, "潮湿导电");
        check(
                hit(cold, DRY).status().frozenTicks() == 40
                        && hit(cold, WET).status().frozenTicks() == 80,
                "潮湿增强寒霜");
        var frozen = new ElementalCombat.State(false, false, 160, false, false, false);
        check(
                damage(hit(earth, frozen), DamageAffinity.EARTH) == Fixed.ONE * 3 / 2
                        && hit(earth, frozen).status().frozenTicks() == 80,
                "既有冻结碎冰");
        check(
                !hit(earth.plus(m(Element.FIRE, true, 4 * Fixed.ONE)), DRY)
                        .reactions()
                        .contains(ElementalCombat.Reaction.SHATTER),
                "同次新冰不自触发碎冰");
        check(hit(fire, frozen).status().frozenTicks() == 80, "火焰解冻");
        check(
                hit(fire.plus(cold), DRY).totalDamage() == 0
                        && !hit(fire.plus(cold), DRY).status().extinguish(),
                "冷热先相消，不按顺序先烧后冻");
        check(
                hit(water.plus(m(Element.WATER, true, Fixed.ONE)), DRY).damage().isEmpty()
                        && hit(water.plus(m(Element.WATER, true, Fixed.ONE)), DRY).status().wet()
                                == ElementalCombat.WetChange.KEEP,
                "同量水与反水相消");
        check(hit(water.plus(earth), DRY).status().slowAmplifier() == 1, "水土泥泞");
        var burning = new ElementalCombat.State(false, true, 0, false, false, false);
        check(
                hit(wind, burning).status().fanTicks() == 20
                        && !hit(water.plus(wind), burning)
                                .reactions()
                                .contains(ElementalCombat.Reaction.FAN),
                "风助燃，水阻止助燃");
        check(
                hit(light, DRY).healing() == 2 * Fixed.ONE
                        && hit(light, DRY).totalDamage() == 0
                        && hit(light, DRY).status().purify(),
                "光治疗并驱暗");
        var undead = new ElementalCombat.State(false, false, 0, false, false, true);
        check(
                hit(light, undead).healing() == 0
                        && damage(hit(light, undead), DamageAffinity.LIGHT) == 4 * Fixed.ONE,
                "正光灼邪不治疗亡灵");
        check(
                hit(light.plus(dark), DRY).healing() == 0
                        && hit(light.plus(dark), DRY).damage().isEmpty()
                        && !hit(light.plus(dark), DRY).status().purify(),
                "光暗按量中和");
        var immune = new ElementalCombat.State(false, false, 0, true, true, false);
        var filtered = hit(fire.plus(cold).plus(lightning), immune);
        check(
                filtered.damage().size() == 1 && filtered.affinity() == DamageAffinity.LIGHTNING,
                "免疫只影响对应分量");
        var mixed =
                hit(
                        fire.plus(lightning),
                        new ElementalCombat.State(false, false, 0, true, false, false));
        check(
                mixed.totalDamage() == 2 * Fixed.ONE
                        && mixed.affinity() == DamageAffinity.LIGHTNING,
                "火免疫不吞其他元素");
        check(
                hit(m(Element.ARCANE, false, Fixed.ONE), DRY).affinity() == DamageAffinity.ARCANE,
                "奥术稳定类型");
        check(hit(m(Element.WIND, true, Fixed.ONE), DRY).status().impulse() < 0, "反风吸引");
        check(
                hit(cold, new ElementalCombat.State(false, false, 10000, false, false, false))
                                .status()
                                .frozenTicks()
                        == 10000,
                "小寒霜不缩短外部高冻结");
        var random = new Random(20261101);
        for (int n = 0; n < 2500; n++) {
            long[] amounts = new long[17];
            for (int i = 0; i < 17; i++) amounts[i] = random.nextLong(Fixed.MAX / 17);
            var m = new Mixture(amounts);
            var state =
                    new ElementalCombat.State(
                            random.nextBoolean(),
                            random.nextBoolean(),
                            random.nextInt(500),
                            random.nextBoolean(),
                            random.nextBoolean(),
                            random.nextBoolean());
            var p1 = ElementalCombat.evaluate(m, 2 * Fixed.ONE, state, R);
            var p2 = ElementalCombat.evaluate(new Mixture(amounts), 2 * Fixed.ONE, state, R);
            check(p1.equals(p2), "反应确定性");
            check(p1.totalDamage() >= 0 && p1.totalDamage() <= R.maxDamage(), "混合伤害全局上限");
            check(p1.healing() >= 0 && p1.healing() <= R.maxDamage(), "治疗上限");
            check(p1.status().frozenTicks() >= 0, "冻结非负");
            check(Math.abs(p1.status().impulse()) <= R.status().maxImpulse(), "冲量有限");
            check(m.equals(new Mixture(amounts)), "反应不修改魔力所有权");
            check(p1.extraFrost(R.maxDamage()).totalDamage() <= R.maxDamage(), "混合原版霜弱点仍受预算约束");
        }
        var maxRules =
                new ElementalCombatRules(
                        1,
                        R.baseDamage(),
                        1024 * Fixed.ONE,
                        R.threshold(),
                        new ElementalCombatRules.Multipliers(
                                8 * Fixed.ONE,
                                8 * Fixed.ONE,
                                8 * Fixed.ONE,
                                8 * Fixed.ONE,
                                8 * Fixed.ONE),
                        R.status());
        check(
                ElementalCombat.evaluate(
                                        m(Element.LIGHTNING, false, Fixed.MAX),
                                        2 * Fixed.ONE,
                                        WET,
                                        maxRules)
                                .totalDamage()
                        == 1024 * Fixed.ONE,
                "最大参数无乘法溢出");
        boolean immutable = false;
        try {
            hit(fire, DRY).damage().clear();
        } catch (UnsupportedOperationException e) {
            immutable = true;
        }
        check(immutable, "只读逐元素结果");
        for (var kind : DamageAffinity.values())
            check(kind.path().startsWith("ec_damage/"), "稳定资源前缀");
        return checks;
    }
}
