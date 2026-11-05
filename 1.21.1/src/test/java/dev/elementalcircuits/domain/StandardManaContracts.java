package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.domain.casting.CastingRules;
import dev.elementalcircuits.domain.casting.TriggeredCircuit;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.RuneParameterValue;
import dev.elementalcircuits.domain.circuit.StandardState;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.examples.StandardCircuit;
import dev.elementalcircuits.domain.mana.ManaOrigin;
import dev.elementalcircuits.domain.mana.ManaPool;
import dev.elementalcircuits.domain.mana.ManaSource;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.domain.mana.StorageSettings;

import java.util.*;

/** Resource accounting, exact input, accumulation, timing and migration regression. */
public final class StandardManaContracts {
    private static int checks;

    private static void check(boolean b, String why) {
        checks++;
        if (!b) throw new AssertionError(why);
    }

    private static void rejects(Runnable action, String why) {
        boolean rejected = false;
        try {
            action.run();
        } catch (RuntimeException expected) {
            rejected = true;
        }
        check(rejected, why);
    }

    private static Rules rules() {
        var r = RuleResources.defaults();
        return new Rules(
                        r.version(),
                        r.material(),
                        r.conversion(),
                        r.inversion(),
                        r.coupling(),
                        r.nodeCapacity(),
                        r.injection(),
                        1,
                        r.window(),
                        r.signalCost(),
                        r.playerCapacity(),
                        r.playerRegen(),
                        r.playerDelay(),
                        r.range(),
                        r.radius(),
                        r.routingRetention(),
                        r.oppositionRetention())
                .withManaSteps(8)
                .withCasting(
                        new CastingRules(
                                2,
                                655,
                                131,
                                Fixed.ONE,
                                Collections.nCopies(17, 58982L),
                                0,
                                0,
                                Fixed.ONE,
                                new StandardState(
                                        1,
                                        0,
                                        0,
                                        2,
                                        1200,
                                        Fixed.ONE,
                                        6554,
                                        32768,
                                        2 * Fixed.ONE,
                                        484968)));
    }

    private static ManaSource source(ManaPool pool) {
        return new ManaSource() {
            public long available() {
                return pool.available();
            }

            public boolean debit(long n) {
                return pool.debit(n);
            }

            public ManaOrigin provenance() {
                return new ManaOrigin("player/00000000-0000-0000-0000-000000000001");
            }
        };
    }

    private static void conservation(TickCircuit c) {
        var s = c.snapshot();
        check(
                s.supplied() == c.held() + s.loss() + s.delivered() + s.castingLedger().refunded(),
                "魔力账本始终守恒");
    }

    private static void exactInput() {
        for (long requested : new long[] {1, 56, 64, 1024, 10000, 65535}) {
            var b =
                    StandardCircuit.create(rules(), false)
                            .withInputAmount(1, requested * Fixed.ONE);
            var c = new TickCircuit(b);
            var pool = new ManaPool(Fixed.MAX, 0, 0);
            c.start();
            c.tick(source(pool));
            check(
                    pool.snapshot().spent() == requested * Fixed.ONE,
                    "输入完整扣款，不能按旧节点容量静默截断：" + requested);
            check(c.snapshot().supplied() == requested * Fixed.ONE, "总注入记录完整请求");
            conservation(c);
        }
        var c =
                new TickCircuit(
                        StandardCircuit.create(rules(), false)
                                .withInputAmount(1, 10000 * Fixed.ONE));
        var poor = new ManaPool(56 * Fixed.ONE, 0, 0);
        c.start();
        c.tick(source(poor));
        check(
                poor.available() == 56 * Fixed.ONE
                        && poor.snapshot().spent() == 0
                        && !c.powered()
                        && c.failures() == 1,
                "只有56时不能把10000降为56或64，原子失败");
        var ignored =
                new Rune(
                        1,
                        Rune.Kind.INPUT,
                        new Hex(0, 0),
                        Element.PURE,
                        10000 * Fixed.ONE,
                        true,
                        false,
                        List.of());
        var current = new Blueprint(5, rules(), List.of(ignored), List.of(), false);
        check(current.inputAmount(current.rune(1)) == 10000 * Fixed.ONE, "当前蓝图直接使用输入符文保存的精确请求量");
        check(
                TriggeredCircuit.plan(current, 56 * Fixed.ONE, 40)
                                .inputAmount(
                                        TriggeredCircuit.plan(current, 56 * Fixed.ONE, 40).rune(1))
                        == 10000 * Fixed.ONE,
                "派生执行保留当前输入请求量");
        var definition = c.definition();
        var planned = TriggeredCircuit.plan(definition, 56 * Fixed.ONE, 40);
        check(planned.inputAmount(planned.rune(1)) == 10000 * Fixed.ONE, "派生阵完整保留请求量");
        rejects(
                () ->
                        new ManaSource() {
                            public long available() {
                                return Fixed.MAX;
                            }

                            public boolean debit(long q) {
                                throw new AssertionError("无效报价不能扣款");
                            }

                            public Mixture preview(long q) {
                                return Mixture.pure(56 * Fixed.ONE);
                            }
                        }.draw(10000 * Fixed.ONE),
                "拒绝供应方隐式截断报价");
    }

    private static void timing() {
        var b = StandardCircuit.create(rules(), false);
        var c = new TickCircuit(b);
        check(
                c.complexity().nodes() == 5
                        && c.complexity().length() == 4
                        && c.complexity().depth() == 4,
                "参考阵结构");
        check(
                c.castingDelay(Fixed.ONE) == 2
                        && c.castingDelay(2 * Fixed.ONE) == 1
                        && c.castingDelay(Fixed.ONE / 2) == 4,
                "标准2拍，速度乘数连续生效");
        var unused = new ArrayList<>(b.runes());
        unused.add(Rune.create(99, Rune.Kind.SIGNAL, new Hex(0, 2)));
        var decorated = new TickCircuit(new Blueprint(5, b.rules(), unused, b.routes(), false));
        check(decorated.complexity().score() == c.complexity().score(), "孤立装饰不影响复杂度");
        var complex = new TickCircuit(StandardCircuit.create(rules(), true));
        check(complex.castingDelay(Fixed.ONE) > c.castingDelay(Fixed.ONE), "有效结构越复杂准备越长");
        var pool = new ManaPool(64 * Fixed.ONE, 0, 0);
        c.start();
        check(c.tick(source(pool)).isEmpty(), "首步不能越过准备延迟");
        var checkpoint = JsonCodecs.readSnapshot(JsonCodecs.snapshot(c));
        check(c.snapshot().version() == 14, "当前现场固定使用协议14");
        check(c.tick(source(pool)).equals(checkpoint.tick(source(pool))), "恢复后下一拍一致");
        var left = c.tick(source(pool));
        var right = checkpoint.tick(source(pool));
        check(left.size() == 1 && left.equals(right), "恢复保留释放时刻与完整付款，没有重置或重复扣款");
        conservation(c);
        conservation(checkpoint);
        var bad = com.google.gson.JsonParser.parseString(JsonCodecs.snapshot(c)).getAsJsonObject();
        bad.addProperty("version", 10);
        rejects(() -> JsonCodecs.readSnapshot(bad.toString()), "旧版本不能伪装新版存储策略");
        var legacy = new TickCircuit(StandardCircuit.create(RuleResources.defaults(), false));
        check(legacy.snapshot().version() == 14 && legacy.castingDelay(Fixed.ONE) == 0, "当前规则独立恢复");
    }

    private static void storage() {
        var b = StandardCircuit.create(rules(), true);
        var c = new TickCircuit(b);
        var pool = new ManaPool(128 * Fixed.ONE, 0, 0);
        c.start();
        int casts = 0;
        for (int t = 0; t < 30; t++) {
            casts += c.tick(source(pool)).size();
            conservation(c);
            if (t < 3)
                check(
                        c.snapshot().local()[1] != null
                                && c.snapshot().local()[1].mana().total() == (t + 1) * Fixed.ONE,
                        "存储按完整输入跨步累积，不提前输出：" + t);
            if (t == 2) {
                c = JsonCodecs.readSnapshot(JsonCodecs.snapshot(c));
                check(
                        c.definition().rune(2).storage().equals(StorageSettings.DEFAULT),
                        "容量、阈值、释放量随蓝图与现场保存");
            }
        }
        check(casts > 1, "持续小额注入可多次蓄能触发输出");
        var one = new TickCircuit(new Blueprint(5, b.rules(), b.runes(), b.routes(), false));
        var supply = new ManaPool(64 * Fixed.ONE, 0, 0);
        for (int t = 0; t < 3; t++) {
            one.start();
            one.tick(source(supply));
            one.stop();
        }
        check(one.snapshot().local()[1].mana().total() == 3 * Fixed.ONE, "多次手动起阵积攒同一存储而不重置");
        var restored = JsonCodecs.readSnapshot(JsonCodecs.snapshot(one));
        check(restored.held() == one.held(), "停供存储完整恢复");
        for (int i = 0; i < 10; i++) one.tick(ManaSource.NONE);
        check(one.snapshot().local()[1].mana().total() == 3 * Fixed.ONE, "停供只保存，不自动放行");
        var signal = new ArrayList<>(b.runes());
        var store = b.rune(2);
        signal.set(
                1,
                new Rune(
                        store.id(),
                        store.kind(),
                        store.cell(),
                        store.element(),
                        store.value(),
                        true,
                        true,
                        store.weights(),
                        new StorageSettings(16 * Fixed.ONE, Fixed.ONE, Fixed.ONE)));
        signal.add(Rune.create(20, Rune.Kind.SIGNAL, new Hex(-1, 1)));
        var wires = new ArrayList<>(b.routes());
        wires.add(new Route(20, 2, Route.Payload.SIGNAL, List.of(new Hex(-1, 1), new Hex(-1, 0))));
        var gated = new TickCircuit(new Blueprint(5, b.rules(), signal, wires, true));
        gated.start();
        gated.tick(source(supply));
        check(
                gated.snapshot().local()[1] != null
                        && gated.snapshot().local()[1].mana().total() == Fixed.ONE,
                "信号控制按上步采样，首步不提前放行");
        gated.tick(source(supply));
        check(gated.snapshot().local()[1].mana().total() == Fixed.ONE, "信号打开后每世界步只放一次，不被8轮传输重复触发");
        rejects(() -> new StorageSettings(4 * Fixed.ONE, 5 * Fixed.ONE, Fixed.ONE), "阈值不能超过容量");
        rejects(() -> new StorageSettings(4 * Fixed.ONE, Fixed.ONE, 2 * Fixed.ONE), "单次释放不能超过阈值");
        var decaying =
                new CastingRules(
                        2,
                        0,
                        0,
                        Fixed.ONE,
                        Collections.nCopies(17, Fixed.ONE),
                        0,
                        0,
                        Fixed.ONE,
                        new StandardState(
                                1,
                                6554,
                                655,
                                0,
                                1200,
                                Fixed.ONE,
                                6554,
                                32768,
                                2 * Fixed.ONE,
                                484968));
        var d = restored.withCasting(decaying);
        long before = d.held();
        d.tick(ManaSource.NONE);
        check(d.held() < before, "专用存储仍有可配置自然损耗");
        conservation(d);
    }

    private static void boundaries() {
        var policy = rules().casting();
        check(policy.retention(62259) > 65000, "线路与转化等历史损耗降至十分之一");
        for (var kind :
                List.of(Rune.Kind.INPUT, Rune.Kind.ABSORB, Rune.Kind.AMOUNT, Rune.Kind.SENSOR))
            check(
                    RuneParameterValue.parse(kind, "10000") == 10000 * Fixed.ONE,
                    "各数量符文没有56/64截断：" + kind);
        for (var kind : List.of(Rune.Kind.RANGE, Rune.Kind.RADIUS))
            rejects(() -> RuneParameterValue.parse(kind, "1.5"), "整数空间参数明确拒绝小数，不静默截断");
        check(RuneParameterValue.parse(Rune.Kind.AMOUNT, "0.33333333333") == 21845, "连续数量按定点量化");
        var b = StandardCircuit.create(rules(), false);
        var c = new TickCircuit(b);
        var pool = new ManaPool(64 * Fixed.ONE, 0, 0);
        c.start();
        for (int t = 0; t < 10; t++) {
            c.tick(source(pool), () -> false);
            conservation(c);
        }
        check(
                c.snapshot().reserve().empty()
                        && Arrays.stream(c.snapshot().inbox()).allMatch(Objects::isNull)
                        && Arrays.stream(c.snapshot().local()).allMatch(Objects::isNull),
                "普通输出不会在冷却时无限保存魔力，没有隐藏公共储备");
        check(c.refunds().stream().mapToLong(r -> r.mana().total()).sum() > 0, "可追溯的未使用量退回真实来源");
    }

    private static void storedOrderAndExpiredDelay() {
        var input =
                new Rune(
                        1,
                        Rune.Kind.INPUT,
                        new Hex(-1, 0),
                        Element.PURE,
                        2 * Fixed.ONE,
                        true,
                        false,
                        List.of());
        var store =
                new Rune(
                        2,
                        Rune.Kind.STORAGE,
                        new Hex(0, 0),
                        Element.PURE,
                        0,
                        true,
                        false,
                        List.of(),
                        new StorageSettings(8 * Fixed.ONE, 4 * Fixed.ONE, 2 * Fixed.ONE));
        var output = Rune.create(3, Rune.Kind.OUTPUT, new Hex(1, 0));
        var b =
                new Blueprint(
                        5,
                        rules(),
                        List.of(input, store, output),
                        List.of(
                                new Route(
                                        1,
                                        2,
                                        Route.Payload.MANA,
                                        List.of(input.cell(), store.cell())),
                                new Route(
                                        2,
                                        3,
                                        Route.Payload.MANA,
                                        List.of(store.cell(), output.cell()))),
                        true);
        var c = new TickCircuit(b);
        var pool = new ManaPool(64 * Fixed.ONE, 0, 0);
        Element[] element = {Element.FIRE};
        var colored =
                new ManaSource() {
                    public long available() {
                        return pool.available();
                    }

                    public boolean debit(long n) {
                        return pool.debit(n);
                    }

                    public ManaOrigin provenance() {
                        return new ManaOrigin("player/00000000-0000-0000-0000-000000000001");
                    }

                    public Mixture preview(long n) {
                        return Mixture.pure(n).convert(element[0], Fixed.ONE).sourced(provenance());
                    }
                };
        c.start();
        c.tick(colored);
        element[0] = Element.WATER;
        c.tick(colored);
        check(
                c.snapshot().local()[1].mana().values()[Element.WATER.component(false)]
                        == 2 * Fixed.ONE,
                "存储按先到顺序释放，先火后水不会被改成比例混合");
        check(
                c.snapshot().inbox()[1].mana().values()[Element.FIRE.component(false)]
                        == 2 * Fixed.ONE,
                "输出队列保持第一批火元素及来源");
        conservation(c);
        var policy = rules().casting();
        var evaporating =
                new CastingRules(
                        2,
                        policy.parameterLossScale(),
                        policy.shapeCostPerBlock(),
                        policy.failureLossScale(),
                        policy.refundRates(),
                        0,
                        Fixed.ONE,
                        Fixed.ONE,
                        policy.standard());
        var d = new TickCircuit(StandardCircuit.create(rules().withCasting(evaporating), false));
        d.start();
        d.tick(source(pool));
        d.tick(source(pool));
        check(Arrays.stream(d.snapshot().wakeTicks()).allMatch(x -> x == 0), "等待魔力耗尽后清除悬空释放时刻");
        check(JsonCodecs.readSnapshot(JsonCodecs.snapshot(d)).held() == 0, "耗散后的延迟状态可恢复");
        conservation(d);
        var old = StandardState.defaults();
        check(old.retention(62259) == 65209, "历史木质5%损失缩减为约0.5%，而非损坏保留率");
    }

    public static int run() {
        checks = 0;
        exactInput();
        timing();
        storage();
        boundaries();
        storedOrderAndExpiredDelay();
        return checks;
    }
}
