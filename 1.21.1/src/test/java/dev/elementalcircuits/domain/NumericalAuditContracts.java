package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.domain.casting.*;
import dev.elementalcircuits.domain.circuit.*;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.examples.StandardCircuit;
import dev.elementalcircuits.domain.mana.*;

import java.util.*;

public final class NumericalAuditContracts {
    private static final long F = Fixed.ONE;
    private static int checks;

    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }

    public static int run() {
        checks = 0;
        var rules =
                dev.elementalcircuits.testing.TestBlueprints.entries().stream()
                        .filter(e -> e.file().equals("standard.ecblueprint"))
                        .findFirst()
                        .orElseThrow()
                        .blueprint()
                        .rules();
        for (long amount : new long[] {1, F, 4 * F, Fixed.MAX})
            for (long cost : new long[] {1, F / 5, F / 2, F, 2 * F, Fixed.MAX}) {
                var spec =
                        new SpellSpec(
                                SpellSpec.Method.AREA, SpellSpec.Shape.SPHERE, amount, 0, 1, 0);
                var q =
                        CastBudget.quote(
                                spec, rules, new CastContext(F, cost), ShapeRules.protocolV1());
                check(
                        q.effect() > 0 && q.effect() <= q.required() && q.effect() <= amount,
                        "折扣下输出只消费已付预算");
                var physical = Mixture.pure(q.effect()).convert(Element.FIRE, F);
                check(q.resolved().effectScale().apply(physical).total() == amount, "折扣保留名义作用量");
                var encoded =
                        JsonCodecs.readOrderedPacket(
                                JsonCodecs.packetText(new Packet(physical, q.resolved())));
                check(encoded.spell().effectScale().equals(q.resolved().effectScale()), "效果比例完整保存");
            }
        for (long cost : new long[] {1, F / 5, F / 2, F, 2 * F}) {
            var vm =
                    new TickCircuit(
                            StandardCircuit.create(rules, false).withInputAmount(1, 32 * F));
            var pool = new ManaPool(64 * F, 0, 0);
            var casts = new ArrayList<MagicCircuit.Cast>();
            vm.start(SupplyMode.PULSE, F);
            for (int tick = 0; tick < 40; tick++) {
                casts.addAll(
                        vm.tick(
                                pool,
                                ReleasePermit.UNLIMITED,
                                ManaSource.NONE,
                                new CastContext(F, cost),
                                ShapeRules.protocolV1()));
                var s = vm.snapshot();
                check(
                        s.supplied()
                                == s.loss()
                                        + s.delivered()
                                        + vm.held()
                                        + s.castingLedger().refunded(),
                        "折扣施法账本逐步守恒");
            }
            check(casts.size() == 1, "极低成本和普通成本均可实际施法");
            var cast = casts.getFirst();
            check(
                    cast.spell().effectScale().apply(cast.mana()).total()
                            == rules.casting().defaultEffect(),
                    "输出效果不随折扣缩水: cost="
                            + cost
                            + " payload="
                            + cast.mana().total()
                            + " scale="
                            + cast.spell().effectScale()
                            + " rules="
                            + rules.casting().version());
            check(cast.mana().total() <= pool.snapshot().spent(), "派生资金不超过已支付资源");
        }
        var pool = new ManaPool(64 * F, 4 * F, 40);
        pool.debit(4 * F);
        pool.restore(4 * F);
        check(
                pool.snapshot().waitTicks() == 0
                        && ManaPool.restore(pool.snapshot(), 4 * F, 40).available() == 64 * F,
                "消耗品补满后可立即保存恢复");
        pool.debit(4 * F);
        pool.restore(F);
        check(pool.snapshot().waitTicks() == 40, "未补满时仍保留恢复等待");
        var ordered =
                Mixture.ordered(
                        List.of(
                                new Mixture.Portion(
                                        Element.FIRE.component(false),
                                        3,
                                        5,
                                        new ManaOrigin("player/a")),
                                new Mixture.Portion(
                                        Element.WATER.component(false),
                                        2,
                                        6,
                                        new ManaOrigin("player/b"))));
        var scaled = new EffectScale(7, 5).apply(ordered);
        check(
                scaled.total() == 7
                        && scaled.order()
                                .getFirst()
                                .origin()
                                .equals(ordered.order().getFirst().origin())
                        && scaled.order().get(1).arrival() == 6,
                "效果折算保留混合先后与来源");
        check(new EffectScale(F, F).equals(EffectScale.IDENTITY), "比例约分为唯一恒等表示");
        var lightRules = dev.elementalcircuits.adapter.resources.RadianceResources.rules();
        var light = Mixture.pure(4 * F).convert(Element.LIGHT, F);
        var nominalLight =
                SpellPayloads.illumination(new Packet(light, SpellSpec.EMPTY), F, lightRules);
        var discountScale = new EffectScale(4, 1);
        var discounted =
                SpellPayloads.illumination(
                        new Packet(light.take(F), SpellSpec.EMPTY.withEffectScale(discountScale)),
                        F,
                        lightRules);
        check(discounted.ticks() == nominalLight.ticks(), "折扣不缩短同等名义光弹的照明时长");
        long lightPaid = F - discounted.impact().mana().total();
        check(
                lightPaid <= F / 4
                        && discountScale.effectAmount(lightPaid)
                                >= discounted.ticks() * lightRules.trailCost(),
                "光照按输出效率真实预付而不增加运输预算");
        return checks;
    }
}
