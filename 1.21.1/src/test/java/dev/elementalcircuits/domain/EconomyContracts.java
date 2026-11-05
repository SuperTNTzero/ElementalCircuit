package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.ElementalCombatResources;
import dev.elementalcircuits.adapter.resources.ManaFieldResources;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.adapter.resources.ShapeResources;
import dev.elementalcircuits.domain.casting.CastBudget;
import dev.elementalcircuits.domain.casting.CastContext;
import dev.elementalcircuits.domain.casting.CastingRules;
import dev.elementalcircuits.domain.casting.RecursionRules;
import dev.elementalcircuits.domain.casting.ReleasePermit;
import dev.elementalcircuits.domain.casting.ShapeFootprint;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.casting.TriggeredCircuit;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.RuneParameterValue;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.combat.ElementalCombat;
import dev.elementalcircuits.domain.equipment.WandWear;
import dev.elementalcircuits.domain.mana.ManaNode;
import dev.elementalcircuits.domain.mana.ManaOrigin;
import dev.elementalcircuits.domain.mana.ManaPool;
import dev.elementalcircuits.domain.mana.ManaSource;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.domain.mana.Packet;

import java.math.*;
import java.util.*;

/**
 * Ownership, fixed budgets, continuous precision and deterministic loss at real tick boundaries.
 */
public final class EconomyContracts {
    private static int checks;
    private static final long F = Fixed.ONE;

    private static void check(boolean v, String why) {
        checks++;
        if (!v) throw new AssertionError(why);
    }

    private static void rejects(Runnable r) {
        boolean failed = false;
        try {
            r.run();
        } catch (RuntimeException ex) {
            failed = true;
        }
        check(failed, "应拒绝非法现场");
    }

    private static CastingRules policy(long moving, long cache) {
        var rates = new ArrayList<>(Collections.nCopies(17, F / 2));
        rates.set(0, F * 9 / 10);
        return new CastingRules(
                1, Math.round(.01 * F), Math.round(.002 * F), F, rates, moving, cache, F);
    }

    private static Blueprint plan(long input, int steps, CastingRules policy) {
        var b =
                AssemblyContracts.line(
                                false,
                                Route.Payload.MANA,
                                Rune.Kind.INPUT,
                                Rune.Kind.CONVERT,
                                Rune.Kind.OUTPUT)
                        .withInputAmount(1, input);
        return new Blueprint(
                b.version(),
                b.rules().withManaSteps(steps).withCasting(policy),
                b.runes(),
                b.routes(),
                false);
    }

    private static ManaSource source(ManaPool pool, String id) {
        return new ManaSource() {
            public long available() {
                return pool.available();
            }

            public boolean debit(long amount) {
                return pool.debit(amount);
            }

            public ManaOrigin provenance() {
                return new ManaOrigin(id);
            }
        };
    }

    private static void ledger(TickCircuit c) {
        var s = c.snapshot();
        check(
                s.supplied() == c.held() + s.loss() + s.delivered() + s.castingLedger().refunded(),
                "失败/返还/缓存与交付守恒");
    }

    public static int run() {
        check(RuneParameterValue.parse(Rune.Kind.AIM, "0.1") == 6554, "连续精度只在定点边界量化");
        check(
                RuneParameterValue.parse(Rune.Kind.AIM, "0.333333333333333333333333333333")
                        == 21845,
                "循环小数不报rounding necessary");
        rejects(() -> RuneParameterValue.parse(Rune.Kind.AIM, "-0.00000001"));
        rejects(() -> RuneParameterValue.parse(Rune.Kind.AIM, "1.0000000001"));
        var a = new ManaOrigin("player/11111111-1111-1111-1111-111111111111");
        var z = new ManaOrigin("field/minecraft:overworld/1,2,3");
        var m = Mixture.pure(20 * F).sourced(a).plus(Mixture.pure(10 * F).sourced(z));
        var taken = m.take(25 * F);
        check(m.minus(taken).order().stream().allMatch(p -> p.origin().equals(z)), "同元素扣除保留真实来源");
        check(
                m.inverse(F).convert(Element.FIRE, F).order().getLast().origin().equals(z),
                "转化不丢失来源");
        var funded =
                Mixture.pure(2)
                        .convert(Element.FIRE, F)
                        .sourced(a)
                        .plus(Mixture.pure(2).convert(Element.WATER, F).sourced(z));
        var firstFunding = funded.takeOrdered(1);
        var nextFunding = funded.minus(firstFunding).takeOrdered(2);
        check(
                firstFunding.plus(nextFunding).sameAmounts(funded.takeOrdered(3)),
                "混合预算连续支取等于同量前缀支取，无累计舍入偏移");
        check(
                funded.minus(funded.takeOrdered(3)).order().getFirst().origin().equals(z),
                "子阵未用预算的来源与实际消费一致");
        long got = 0;
        for (var part : m.split(new int[] {2, 3, 7})) got += part.total();
        check(got == m.total(), "来源分流守恒");
        check(
                Mixture.pure(Fixed.MAX).divide(1, 100000).total() == Fixed.MAX / 100000,
                "大除数先高精度除法再截断，不预先取零保留率");
        var r = RuleResources.defaults().withCasting(policy(0, 0));
        var implicit =
                CastBudget.quote(SpellSpec.EMPTY, r, CastContext.NEUTRAL, ShapeResources.rules());
        var explicit =
                CastBudget.quote(
                        CastBudget.resolve(SpellSpec.EMPTY, r),
                        r,
                        CastContext.NEUTRAL,
                        ShapeResources.rules());
        check(implicit.required() == explicit.required(), "默认参数和显式同值费用相同");
        check(implicit.footprint() == 33, "半径二球覆盖33个逻辑方块");
        check(implicit.required() > F && implicit.required() < 2 * F, "形状成本按方块数，参数损耗低于百分之一");
        var high =
                CastBudget.quote(
                        new SpellSpec(
                                SpellSpec.Method.DIRECTED, SpellSpec.Shape.BEAM, F, 1024, 0, 0),
                        r,
                        CastContext.NEUTRAL,
                        ShapeResources.rules());
        check(high.required() > 0 && high.required() < 4 * F, "大距离高精度报价不归零");
        var p1 =
                CastBudget.quote(
                        new SpellSpec(
                                SpellSpec.Method.DIRECTED, SpellSpec.Shape.BEAM, F, 12, 0, 6553),
                        r,
                        CastContext.NEUTRAL,
                        ShapeResources.rules());
        var p2 =
                CastBudget.quote(
                        new SpellSpec(
                                SpellSpec.Method.DIRECTED, SpellSpec.Shape.BEAM, F, 12, 0, 6554),
                        r,
                        CastContext.NEUTRAL,
                        ShapeResources.rules());
        check(p1.parameterDivisor() != p2.parameterDivisor(), "精度连续影响除数，不再分成四档");
        var x =
                CastBudget.quote(
                        SpellSpec.EMPTY, r, new CastContext(2 * F, 2 * F), ShapeResources.rules());
        check(
                x.required() >= implicit.required() * 2
                        && x.required() <= implicit.required() * 2 + 1,
                "指数一时成本线性随法强增长");
        check(x.resolved().damageMultiplier() == 2 * F, "输出冻结施法者法强");
        for (var shape : SpellSpec.Shape.values())
            if (shape != SpellSpec.Shape.UNSET) {
                var spec =
                        CastBudget.resolve(
                                new SpellSpec(
                                        SpellSpec.Method.DIRECTED,
                                        shape,
                                        F,
                                        shape == SpellSpec.Shape.FAN
                                                        || shape == SpellSpec.Shape.SHIELD
                                                ? 0
                                                : 12,
                                        shape == SpellSpec.Shape.BEAM ? 0 : 2,
                                        8192),
                                r);
                check(ShapeFootprint.blocks(spec, ShapeResources.rules()) > 0, "每个形状都有独立占格报价");
            }
        var vm = new TickCircuit(plan(F, 8, policy(0, 0)));
        var pool = new ManaPool(64 * F, 0, 0);
        vm.start();
        var casts = vm.tick(source(pool, a.key()));
        check(casts.isEmpty() && vm.snapshot().castingLedger().failures() == 1, "固定一魔力预算不足时明确失败");
        check(pool.snapshot().spent() == F, "失败不额外抽取供给");
        check(vm.refunds().size() == 1, "失败生成来源返还凭据");
        check(vm.refunds().getFirst().mana().order().getFirst().origin().equals(a), "元素转化后返还原玩家");
        ledger(vm);
        String text = JsonCodecs.snapshot(vm);
        vm = JsonCodecs.readSnapshot(text);
        check(JsonCodecs.snapshot(vm).equals(text), "待返还现场可精确继续");
        var ticket = vm.refunds().getFirst();
        long refund = ticket.mana().total();
        vm.acknowledgeRefund(ticket.sequence());
        check(pool.refund(refund) == refund, "原来源可接受净消费返还");
        ledger(vm);
        var finalVm = vm;
        rejects(() -> finalVm.acknowledgeRefund(ticket.sequence()));
        var empty = new TickCircuit(plan(4 * F, 8, policy(0, 0)));
        empty.start();
        empty.tick(new ManaPool(F, 0, 0));
        check(
                empty.failures() == 1 && !empty.powered() && empty.snapshot().supplied() == 0,
                "玩家槽不足在预扣前拒绝，也记为一次失败");
        var exhaustedTree =
                com.google.gson.JsonParser.parseString(
                                JsonCodecs.snapshot(new TickCircuit(plan(4 * F, 8, policy(0, 0)))))
                        .getAsJsonObject();
        exhaustedTree.getAsJsonObject("castingLedger").addProperty("failures", Long.MAX_VALUE);
        var exhausted = JsonCodecs.readSnapshot(exhaustedTree.toString());
        exhausted.start();
        var noDebit = new ManaPool(64 * F, 0, 0);
        exhausted.tick(noDebit);
        check(
                noDebit.snapshot().spent() == 0
                        && exhausted.snapshot().clock() == 0
                        && !exhausted.powered(),
                "失败计数耗尽在支取和状态变更前拒绝推进");
        var unknown = new TickCircuit(plan(F, 8, policy(0, 0)));
        unknown.start();
        unknown.tick(new ManaPool(64 * F, 0, 0));
        check(
                unknown.refunds().isEmpty() && unknown.snapshot().castingLedger().failures() == 1,
                "历史未知来源不得凭空归给玩家");
        ledger(unknown);
        var success = new TickCircuit(plan(4 * F, 8, policy(0, 0)));
        var pool2 = new ManaPool(64 * F, 0, 0);
        success.start();
        casts =
                success.tick(
                        source(pool2, z.key()),
                        ReleasePermit.UNLIMITED,
                        ManaSource.NONE,
                        new CastContext(2 * F, 2 * F),
                        ShapeResources.rules());
        check(
                casts.size() == 1
                        && casts.getFirst().mana().total() == F
                        && casts.getFirst().spell().damageMultiplier() == 2 * F,
                "固定输入足额才输出指定作用量");
        ledger(success);
        check(
                success.refunds().isEmpty()
                        && success.failures() == 0
                        && !success.snapshot().reserve().empty(),
                "成功仅结算一次，余款进衰减储备，不立即重试造成假失败");
        var base =
                AssemblyContracts.line(
                        false,
                        Route.Payload.MANA,
                        Rune.Kind.INPUT,
                        Rune.Kind.SWITCH,
                        Rune.Kind.OUTPUT);
        var nodes =
                base.runes().stream()
                        .map(
                                n ->
                                        n.kind() == Rune.Kind.SWITCH
                                                ? new Rune(
                                                        n.id(),
                                                        n.kind(),
                                                        n.cell(),
                                                        n.element(),
                                                        n.value(),
                                                        false,
                                                        n.signalControl(),
                                                        n.weights())
                                                : n)
                        .toList();
        for (int speed : new int[] {1, 8, 64}) {
            var b =
                    new Blueprint(
                            base.version(),
                            base.rules().withManaSteps(speed).withCasting(policy(F / 100, F / 20)),
                            nodes,
                            base.routes(),
                            false);
            var c = new TickCircuit(b);
            c.start();
            var s = new ManaPool(64 * F, 0, 0);
            for (int i = 0; i < 10; i++) c.tick(source(s, a.key()));
            c.stop();
            long before = c.held();
            for (int i = 0; i < 20; i++) c.tick(source(s, a.key()));
            check(c.held() < before / 2, "缓存按世界tick快速衰减");
            ledger(c);
            var copy = JsonCodecs.readSnapshot(JsonCodecs.snapshot(c));
            c.tick(source(s, a.key()));
            copy.tick(source(s, a.key()));
            check(JsonCodecs.snapshot(c).equals(JsonCodecs.snapshot(copy)), "衰减完整保存，无隐式余数");
        }
        check(WandWear.cost(F, F, 12, 16, 2, 1, 1) == 1, "安全区基础耐久一");
        check(WandWear.cost(32 * F, F, 12, 16, 2, 1, 1) == 2, "越过强度阈值按平方增长");
        check(WandWear.cost(F, F, 2, 16, 2, 1, 1) == 17, "越过速度阈值按平方增长");
        check(WandWear.cost(32 * F, F, 12, 32, 2, 1, 1) == 1, "杖端阈值改变曲线拐点");
        var node = new ManaNode(ManaFieldResources.defaults(), ManaNode.Kind.NATURAL);
        node.emitted(F, 2 * F);
        for (int i = 0; i < 20; i++) node.regenerate(F / 2, 1);
        check(node.snapshot().regenerated() == F / 2, "天然节点每秒恢复并保存余数");
        check(
                ManaNode.restore(ManaFieldResources.defaults(), node.snapshot())
                        .snapshot()
                        .equals(node.snapshot()),
                "节点恢复账本守恒");
        var artificial = new ManaNode(ManaFieldResources.defaults(), ManaNode.Kind.ARTIFICIAL);
        check(artificial.regenerate(F, 20) == 0, "人工节点不免费自然补能");
        var rec = new RecursionRules(1, 4, 32, 80, F * 95 / 100);
        var def = plan(4 * F, 8, policy(0, 0));
        var child = new TriggeredCircuit(def, Mixture.pure(F).sourced(a), rec);
        check(
                child.step().isEmpty() && child.done() && child.unspent() == F,
                "派生预算不足保持固定输入，不自动降低参数");
        check(child.unspentRefund().order().getFirst().origin().equals(a), "未注入子阵预算按原来源返还");
        var paidChild =
                new TriggeredCircuit(plan(F, 8, policy(0, 0)), Mixture.pure(2 * F).sourced(z), rec);
        paidChild.step();
        check(
                !paidChild.refunds().isEmpty()
                        && paidChild
                                .refunds()
                                .getFirst()
                                .mana()
                                .order()
                                .getFirst()
                                .origin()
                                .equals(z),
                "派生输入保留原始供给来源");
        var combat = ElementalCombatResources.rules();
        var state = new ElementalCombat.State(false, false, 0, false, false, false);
        var fire = Mixture.pure(F).convert(Element.FIRE, F);
        var one = ElementalCombat.evaluate(fire, F, state, combat);
        var two = ElementalCombat.evaluate(fire, F, state, combat, 2 * F);
        check(two.totalDamage() == 2 * one.totalDamage(), "玩家法强只乘伤害，不隐式制造魔力");
        var powered =
                new SpellSpec(SpellSpec.Method.DIRECTED, SpellSpec.Shape.SPHERE, 0, 12, 2, 0)
                        .withDamageMultiplier(3 * F);
        var packet =
                JsonCodecs.readOrderedPacket(
                        JsonCodecs.packetText(new Packet(fire.sourced(z), powered)));
        check(
                packet.spell().damageMultiplier() == 3 * F
                        && packet.mana().order().getFirst().origin().equals(z),
                "飞行包保存法强及供给来源");
        return checks;
    }
}
