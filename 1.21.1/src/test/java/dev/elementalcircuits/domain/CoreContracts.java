package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.ManaPool;
import dev.elementalcircuits.domain.mana.Mixture;

import java.util.*;

/** Executable contracts: no Minecraft boot, rendering, network or testing framework required. */
public final class CoreContracts {
    private static int assertions;

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static void rejects(Runnable action, String message) {
        boolean rejected = false;
        try {
            action.run();
        } catch (RuntimeException expected) {
            rejected = true;
        }
        check(rejected, message);
    }

    private static Rules rules() {
        return RuleResources.defaults();
    }

    private static ManaPool pool() {
        return new ManaPool(64 * Fixed.ONE, 4 * Fixed.ONE, 40);
    }

    private static Blueprint line(boolean continuous, Rune.Kind... kinds) {
        List<Rune> nodes = new ArrayList<>();
        List<Route> edges = new ArrayList<>();
        for (int i = 0; i < kinds.length; i++)
            nodes.add(Rune.create(i + 1, kinds[i], new Hex(i - 2, 0)));
        for (int i = 1; i < kinds.length; i++)
            edges.add(
                    new Route(
                            i,
                            i + 1,
                            Route.Payload.MANA,
                            List.of(nodes.get(i - 1).cell(), nodes.get(i).cell())));
        return new Blueprint(5, rules(), nodes, edges, continuous);
    }

    private static void ledger(TickCircuit c) {
        var s = c.snapshot();
        check(s.supplied() == s.loss() + s.delivered() + c.held(), "魔力账本必须严格守恒");
    }

    private static void mixture() {
        Random rng = new Random(27);
        for (int n = 0; n < 200; n++) {
            long[] values = new long[17];
            for (int i = 0; i < values.length; i++) values[i] = rng.nextInt(100000);
            Mixture m = new Mixture(values);
            List<Mixture> shares = m.split(new int[] {2, 1, 0, 1});
            Mixture sum = Mixture.EMPTY;
            for (Mixture p : shares) sum = sum.plus(p);
            check(sum.sameAmounts(m), "逐成分分流守恒");
            check(shares.get(2).empty(), "零权重不分配");
            long amount = rng.nextInt((int) m.total() + 1);
            Mixture paid = m.take(amount);
            check(paid.total() == amount && paid.plus(m.minus(paid)).sameAmounts(m), "比例支取精确守恒");
        }
        check(Mixture.pure(1).split(new int[] {1, 1}).get(0).total() == 1, "余数归固定第一出口");
        rejects(() -> Mixture.pure(1).split(new int[] {0, 0}), "拒绝全零权重");
        check(
                Mixture.pure(Fixed.ONE).convert(Element.FIRE, Fixed.ONE).dominant() == Element.FIRE,
                "纯净转元素");
        check(
                Mixture.pure(Fixed.ONE).convert(Element.ARCANE, Fixed.ONE).dominant()
                        == Element.ARCANE,
                "奥术不等于纯净");
        long[] v = new long[17];
        v[Element.FIRE.component(false)] = 33;
        Mixture fire = new Mixture(v);
        check(fire.inverse(Fixed.ONE).values()[Element.FIRE.component(true)] == 33, "元素反相独立");
        rejects(() -> new Mixture(new long[1]), "拒绝错误成分数量");
        check(Mixture.pure(Fixed.MAX).take(Fixed.MAX).total() == Fixed.MAX, "上界支取的乘法不溢出");
        rejects(() -> fire.take(34), "拒绝超额支取");
    }

    private static void topology() {
        for (int radius : new int[] {4, 6, 8}) {
            int count = 0;
            for (int q = -radius; q <= radius; q++)
                for (int r = -radius; r <= radius; r++) if (new Hex(q, r).inside(radius)) count++;
            check(count == 1 + 3 * radius * (radius + 1), "61/127/217 格盘");
        }
        Blueprint b = line(false, Rune.Kind.INPUT, Rune.Kind.CONVERT, Rune.Kind.OUTPUT);
        rejects(
                () ->
                        new Blueprint(
                                5,
                                rules(),
                                b.runes(),
                                List.of(
                                        new Route(
                                                1,
                                                3,
                                                Route.Payload.MANA,
                                                List.of(new Hex(-2, 0), new Hex(0, 0)))),
                                false),
                "拒绝跳格");
        rejects(
                () ->
                        new Blueprint(
                                5,
                                rules(),
                                b.runes(),
                                List.of(
                                        new Route(
                                                1,
                                                3,
                                                Route.Payload.MANA,
                                                List.of(
                                                        new Hex(-2, 0),
                                                        new Hex(-1, 0),
                                                        new Hex(0, 0)))),
                                false),
                "拒绝穿符");
        rejects(
                () ->
                        new Blueprint(
                                5,
                                rules(),
                                b.runes(),
                                List.of(
                                        new Route(
                                                1,
                                                2,
                                                Route.Payload.SIGNAL,
                                                List.of(new Hex(-2, 0), new Hex(-1, 0)))),
                                false),
                "不隐式转换载荷类型");
        rejects(
                () ->
                        new Blueprint(
                                5,
                                rules(),
                                b.runes(),
                                List.of(b.routes().get(0), b.routes().get(0)),
                                false),
                "拒绝重复线路");
        // Crossing cells do not create an implicit junction.
        List<Rune> nodes =
                List.of(
                        Rune.create(1, Rune.Kind.INPUT, new Hex(-1, 0)),
                        Rune.create(2, Rune.Kind.OUTPUT, new Hex(1, 0)),
                        Rune.create(3, Rune.Kind.INPUT, new Hex(0, -1)),
                        Rune.create(4, Rune.Kind.OUTPUT, new Hex(0, 1)));
        Blueprint crossed =
                new Blueprint(
                        5,
                        rules(),
                        nodes,
                        List.of(
                                new Route(
                                        1,
                                        2,
                                        Route.Payload.MANA,
                                        List.of(new Hex(-1, 0), new Hex(0, 0), new Hex(1, 0))),
                                new Route(
                                        3,
                                        4,
                                        Route.Payload.MANA,
                                        List.of(new Hex(0, -1), new Hex(0, 0), new Hex(0, 1)))),
                        false);
        check(crossed.routes().size() == 2, "交叉共格合法且仍为两条线路");
    }

    private static void mana() {
        ManaPool p = pool();
        check(!p.debit(65 * Fixed.ONE) && p.available() == 64 * Fixed.ONE, "供给原子失败");
        p.debit(Fixed.ONE);
        for (int i = 0; i < 40; i++) p.tick();
        check(p.available() == 63 * Fixed.ONE, "消费后等待40步");
        for (int i = 0; i < 5; i++) p.tick();
        check(p.available() == 64 * Fixed.ONE, "固定步恢复4每秒");
        ManaPool restored = JsonCodecs.readPool(JsonCodecs.pool(p), 4 * Fixed.ONE, 40);
        check(restored.snapshot().equals(p.snapshot()), "玩家槽完整恢复");
    }

    private static void runtime() {
        Blueprint b =
                line(
                        false,
                        Rune.Kind.INPUT,
                        Rune.Kind.CONVERT,
                        Rune.Kind.DIRECTED,
                        Rune.Kind.BEAM,
                        Rune.Kind.OUTPUT);
        TickCircuit a = new TickCircuit(b);
        ManaPool p = pool();
        check(a.start(), "手动起阵");
        long output = 0;
        for (int tick = 0; tick < 50; tick++) {
            var result = a.tick(p);
            if (tick < 4) check(result.isEmpty(), "每条线路至少一步因果");
            for (var cast : result) {
                output += cast.mana().total();
                check(
                        cast.spell().method() == SpellSpec.Method.DIRECTED
                                && cast.spell().shape() == SpellSpec.Shape.BEAM,
                        "具体方式和形状独立组合");
            }
            ledger(a);
            TickCircuit restored = JsonCodecs.readSnapshot(JsonCodecs.snapshot(a));
            ManaPool p2 = JsonCodecs.readPool(JsonCodecs.pool(p), 4 * Fixed.ONE, 40);
            TickCircuit clone = TickCircuit.restore(a.snapshot());
            check(JsonCodecs.snapshot(restored).equals(JsonCodecs.snapshot(clone)), "任意边界现场往返");
            check(
                    restored.tick(p2)
                            .equals(
                                    clone.tick(
                                            JsonCodecs.readPool(
                                                    JsonCodecs.pool(p), 4 * Fixed.ONE, 40))),
                    "恢复后输出轨迹一致");
            check(JsonCodecs.snapshot(restored).equals(JsonCodecs.snapshot(clone)), "恢复后内部缓存与时钟一致");
        }
        check(output > 0 && output < 4 * Fixed.ONE, "真实输出有材质与转换损耗");
        check(!a.powered(), "单次窗口结束");
        check(p.snapshot().spent() == 4 * Fixed.ONE, "单次仅注入一次");
        TickCircuit separate = new TickCircuit(b);
        check(separate.snapshot().clock() == 0 && separate.held() == 0, "同定义实例隔离");
        check(
                a.reconfigure(Blueprint.empty(rules())).snapshot().loss() == a.snapshot().loss(),
                "重配不能清除历史损耗");
        TickCircuit sustained =
                new TickCircuit(line(true, Rune.Kind.INPUT, Rune.Kind.CONVERT, Rune.Kind.OUTPUT));
        ManaPool finite = pool();
        sustained.start();
        for (int i = 0; i < 300; i++) {
            sustained.tick(finite);
            ledger(sustained);
        }
        check(!sustained.powered(), "持续法阵真实耗尽供给");
        for (int i = 0; i < 400; i++) {
            finite.tick();
            sustained.tick(finite);
        }
        check(!sustained.powered(), "补能不自动重启");
        check(sustained.start(), "手动重新启动");
        sustained.tick(finite);
        check(sustained.powered(), "补能手动恢复");
    }

    private static void gates() {
        Rune input = Rune.create(1, Rune.Kind.INPUT, new Hex(-1, 0)),
                gate =
                        new Rune(
                                2,
                                Rune.Kind.SWITCH,
                                new Hex(0, 0),
                                Element.FIRE,
                                0,
                                false,
                                false,
                                List.of(1)),
                out = Rune.create(3, Rune.Kind.OUTPUT, new Hex(1, 0));
        Blueprint b =
                new Blueprint(
                        5,
                        rules(),
                        List.of(input, gate, out),
                        List.of(
                                new Route(
                                        1,
                                        2,
                                        Route.Payload.MANA,
                                        List.of(input.cell(), gate.cell())),
                                new Route(
                                        2,
                                        3,
                                        Route.Payload.MANA,
                                        List.of(gate.cell(), out.cell()))),
                        false);
        TickCircuit c = new TickCircuit(b);
        c.start();
        ManaPool p = pool();
        for (int i = 0; i < 45; i++) {
            check(c.tick(p).isEmpty(), "关闭阀门不输出");
            ledger(c);
        }
        check(c.held() > 0, "关闭缓存保留");
        check(c.editable(), "停供后允许有界回收再编辑");
        TickCircuit reconfigured = c.reconfigure(line(false, Rune.Kind.INPUT, Rune.Kind.OUTPUT));
        check(reconfigured.held() == c.held(), "改图回收未交付成分，不返损耗");
        ledger(reconfigured);
        check(reconfigured.snapshot().reserve().total() == c.held(), "回收魔力归法杖储备");
        check(
                JsonCodecs.readSnapshot(JsonCodecs.snapshot(reconfigured)).held() == c.held(),
                "回收储备完整保存");
        TickCircuit restored = JsonCodecs.readSnapshot(JsonCodecs.snapshot(c));
        restored.toggleManual(2);
        long output = 0;
        for (int i = 0; i < 10; i++) {
            for (var cast : restored.tick(p)) output += cast.mana().total();
            ledger(restored);
        }
        check(output > 0, "停供后手动打开能释放原有缓存");
        // A NOT gate begins with false input then sees the prior tick's source signal.
        Rune signal = Rune.create(1, Rune.Kind.SIGNAL, new Hex(0, 0)),
                not = Rune.create(2, Rune.Kind.NOT, new Hex(1, 0)),
                i = Rune.create(3, Rune.Kind.INPUT, new Hex(-1, 0));
        Blueprint sig =
                new Blueprint(
                        5,
                        rules(),
                        List.of(signal, not, i),
                        List.of(
                                new Route(
                                        1,
                                        2,
                                        Route.Payload.SIGNAL,
                                        List.of(signal.cell(), not.cell()))),
                        true);
        TickCircuit s = new TickCircuit(sig);
        s.start();
        s.tick(pool());
        check(s.snapshot().signals()[0], "信号常量首步发布，下一步消费");
    }

    private static void distributionAndAnd() {
        List<Rune> nodes =
                List.of(
                        Rune.create(1, Rune.Kind.INPUT, new Hex(-1, 0)),
                        new Rune(
                                2,
                                Rune.Kind.SPLIT,
                                new Hex(0, 0),
                                Element.FIRE,
                                0,
                                true,
                                false,
                                List.of(2, 1)),
                        Rune.create(3, Rune.Kind.OUTPUT, new Hex(1, 0)),
                        new Rune(
                                4,
                                Rune.Kind.SWITCH,
                                new Hex(0, 1),
                                Element.FIRE,
                                0,
                                false,
                                false,
                                List.of(1)),
                        Rune.create(5, Rune.Kind.OUTPUT, new Hex(0, 2)));
        List<Route> routes =
                List.of(
                        new Route(
                                1,
                                2,
                                Route.Payload.MANA,
                                List.of(nodes.get(0).cell(), nodes.get(1).cell())),
                        new Route(
                                2,
                                3,
                                Route.Payload.MANA,
                                List.of(nodes.get(1).cell(), nodes.get(2).cell())),
                        new Route(
                                2,
                                4,
                                Route.Payload.MANA,
                                List.of(nodes.get(1).cell(), nodes.get(3).cell())),
                        new Route(
                                4,
                                5,
                                Route.Payload.MANA,
                                List.of(nodes.get(3).cell(), nodes.get(4).cell())));
        TickCircuit c = new TickCircuit(new Blueprint(5, rules(), nodes, routes, true));
        ManaPool p = pool();
        c.start();
        long output = 0;
        for (int i = 0; i < 180; i++) {
            for (var cast : c.tick(p)) output += cast.mana().total();
            ledger(c);
        }
        check(output > 0 && c.held() > 0, "一支关闭仍有另一支输出，关闭份额保留");
        TickCircuit restored = JsonCodecs.readSnapshot(JsonCodecs.snapshot(c));
        restored.toggleManual(4);
        c.toggleManual(4);
        ManaPool p2 = JsonCodecs.readPool(JsonCodecs.pool(p), 4 * Fixed.ONE, 40);
        for (int i = 0; i < 10; i++) {
            check(c.tick(p).equals(restored.tick(p2)), "堵塞支路恢复后的输出序列一致");
            ledger(c);
            ledger(restored);
        }
        Rune in1 = Rune.create(1, Rune.Kind.INPUT, new Hex(-1, 0)),
                in2 = Rune.create(2, Rune.Kind.INPUT, new Hex(0, -1)),
                and = Rune.create(3, Rune.Kind.AND, new Hex(0, 0)),
                out = Rune.create(4, Rune.Kind.OUTPUT, new Hex(1, 0));
        Blueprint b =
                new Blueprint(
                        5,
                        rules(),
                        List.of(in1, in2, and, out),
                        List.of(
                                new Route(
                                        1, 3, Route.Payload.MANA, List.of(in1.cell(), and.cell())),
                                new Route(
                                        2, 3, Route.Payload.MANA, List.of(in2.cell(), and.cell())),
                                new Route(
                                        3, 4, Route.Payload.MANA, List.of(and.cell(), out.cell()))),
                        false);
        TickCircuit a = new TickCircuit(b);
        ManaPool supply = pool();
        a.start();
        long delivered = 0;
        for (int i = 0; i < 10; i++) {
            for (var cast : a.tick(supply)) delivered += cast.mana().total();
            ledger(a);
        }
        check(delivered > 4 * Fixed.ONE && delivered < 8 * Fixed.ONE, "与门合并两条独立入线且不复制");
    }

    private static void hostile() {
        String text = JsonCodecs.blueprint(line(false, Rune.Kind.INPUT, Rune.Kind.OUTPUT));
        check(JsonCodecs.readBlueprint(text).equals(JsonCodecs.readBlueprint(text)), "纯配置往返");
        rejects(
                () -> JsonCodecs.readBlueprint(text.replace("\"version\":1", "\"version\":1.5")),
                "编号小数不得截断");
        rejects(
                () -> JsonCodecs.readBlueprint(text.replace("\"version\":1", "\"version\":1e0")),
                "指数编号拒绝");
        rejects(() -> JsonCodecs.readBlueprint("[".repeat(40) + "]".repeat(40)), "拒绝深层嵌套");
        rejects(() -> JsonCodecs.readBlueprint(" ".repeat(70000)), "拒绝超大蓝图");
        TickCircuit c = new TickCircuit(line(false, Rune.Kind.INPUT, Rune.Kind.OUTPUT));
        c.start();
        c.tick(pool());
        String save = JsonCodecs.snapshot(c);
        rejects(
                () ->
                        JsonCodecs.readSnapshot(
                                save.replace("\"supplied\":262144", "\"supplied\":262145")),
                "伪造账本拒绝");
        rejects(() -> new Hex(Integer.MIN_VALUE, 0), "拒绝最小整数坐标");
    }

    public static void main(String[] args) {
        assertions += ControlObservationContracts.run();
        assertions += ConnectionContracts.run();
        assertions += AutomationContracts.run();
        assertions += BoardSafetyContracts.run();
        assertions += CastingUpgradeContracts.run();
        assertions += NumericalAuditContracts.run();
        assertions += EditorInteractionContracts.run();
        assertions += StandardManaContracts.run();
        assertions += WizardEquipmentContracts.run();
        assertions += EconomyContracts.run();
        mixture();
        topology();
        mana();
        runtime();
        gates();
        distributionAndAnd();
        hostile();
        assertions += EditorEquipmentContracts.run();
        assertions += DFlipFlopContracts.run();
        assertions += WorldEffectContracts.run();
        assertions += RadianceBlueprintContracts.run();
        assertions += EditorPointerContracts.run();
        assertions += AssemblyContracts.run();
        assertions += LogicContracts.run();
        assertions += ProjectileContracts.run();
        assertions += SecondaryContracts.run();
        assertions += BookContracts.run();
        assertions += RuntimeArchitectureContracts.run();
        assertions += StateMachineContracts.run();
        assertions += VisualContracts.run();
        assertions += AudioContracts.run();
        assertions += CooldownContracts.run();
        assertions += ManaFieldContracts.run();
        assertions += ShapeContracts.run();
        assertions += BookMotionContracts.run();
        assertions += ManaProgressionContracts.run();
        assertions += ManaRecoveryContracts.run();
        assertions += ElementalCombatContracts.run();
        assertions += QueuedElementContracts.run();
        assertions += ElementalVisualContracts.run();
        assertions += ServerPolicyContracts.run();
        assertions += ManaTransportContracts.run();
        assertions += RecursionContracts.run();
        assertions += ProbeContracts.run();
        assertions += RuneMaterialsContracts.run();
        System.out.println("CORE CONTRACTS PASSED: " + assertions);
    }
}
