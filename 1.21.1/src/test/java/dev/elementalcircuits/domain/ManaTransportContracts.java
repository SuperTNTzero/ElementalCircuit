package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.AssemblyResources;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.domain.casting.ReleasePermit;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.CircuitAssembly;
import dev.elementalcircuits.domain.circuit.MagicCircuit;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.mana.ManaPool;
import dev.elementalcircuits.domain.mana.ManaSource;
import dev.elementalcircuits.domain.mana.Mixture;

import java.util.*;

/** Timing, causal control, exact route arithmetic and lossless live policy migration. */
public final class ManaTransportContracts {
    private static int checks;

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    private static void rejects(Runnable r) {
        boolean bad = false;
        try {
            r.run();
        } catch (RuntimeException e) {
            bad = true;
        }
        check(bad, "非法传输策略必须拒绝");
    }

    private static ManaPool supply() {
        return new ManaPool(Fixed.MAX, 0, 0);
    }

    private static Blueprint speed(Blueprint b, int steps) {
        return new Blueprint(
                b.version(), b.rules().withManaSteps(steps), b.runes(), b.routes(), b.continuous());
    }

    private static void ledger(TickCircuit c) {
        var s = c.snapshot();
        check(s.supplied() == s.loss() + s.delivered() + c.held(), "微步资源严格守恒");
    }

    private static void timings() {
        var b =
                AssemblyContracts.line(
                        false,
                        Route.Payload.MANA,
                        Rune.Kind.INPUT,
                        Rune.Kind.CONVERT,
                        Rune.Kind.DIRECTED,
                        Rune.Kind.BEAM,
                        Rune.Kind.OUTPUT);
        var slow = new TickCircuit(b);
        var fast = new TickCircuit(speed(b, 8));
        slow.start();
        fast.start();
        var p = supply();
        var q = supply();
        List<MagicCircuit.Cast> expected = new ArrayList<>();
        int ticks = 0;
        while (expected.isEmpty() && ticks++ < 20) expected.addAll(slow.tick(p));
        var actual = fast.tick(q);
        check(ticks == 5 && actual.equals(expected), "五层法阵从250ms变为首个50ms步输出，组成和损耗相同");
        check(
                fast.snapshot().clock() == 1
                        && fast.snapshot().remaining() == b.rules().window() - 1,
                "八轮传输仅消耗一个逻辑步窗口");
        check(
                p.snapshot().spent() == q.snapshot().spent()
                        && slow.snapshot().loss() == fast.snapshot().loss(),
                "输入收费和长度损耗不随速度倍增");
        ledger(fast);
        for (int steps : new int[] {1, 2, 3, 8, 64}) {
            var c = new TickCircuit(speed(b, steps));
            c.start();
            var pool = supply();
            int first = 0, count = 0;
            while (count == 0 && first++ < 10) count = c.tick(pool).size();
            check(count == 1 && first == (5 + steps - 1) / steps, "首发延迟按传输轮数缩短");
            ledger(c);
        }
        var continuous =
                new TickCircuit(
                        speed(
                                new Blueprint(b.version(), b.rules(), b.runes(), b.routes(), true),
                                64));
        continuous.start();
        p = supply();
        for (int i = 1; i <= 25; i++) {
            check(
                    continuous.tick(p).size() == ((i - 1) % b.rules().interval() == 0 ? 1 : 0),
                    "持续阵注入间隔保留原节拍");
            check(
                    continuous.snapshot().supplied()
                            == ((i - 1) / b.rules().interval() + 1) * b.rules().injection(),
                    "高速阵不额外扣除输入");
            ledger(continuous);
        }
    }

    private static void control() {
        var b =
                AssemblyContracts.line(
                        true,
                        Route.Payload.SIGNAL,
                        Rune.Kind.SIGNAL_RECEIVE,
                        Rune.Kind.COUNTER,
                        Rune.Kind.SIGNAL_SEND);
        var a = new TickCircuit(b);
        var c = new TickCircuit(speed(b, 64));
        a.start();
        c.start();
        var p = supply();
        var q = supply();
        for (int tick = 0; tick < 50; tick++) {
            boolean high = tick % 6 >= 3;
            a.clearDrives();
            c.clearDrives();
            a.drive(1, high);
            c.drive(1, high);
            a.tick(p);
            c.tick(q);
            check(
                    a.logic(2).equals(c.logic(2))
                            && a.signal(3) == c.signal(3)
                            && a.validSignal(3) == c.validSignal(3),
                    "计数器沿检测、信号延迟与有效性保持20Hz");
            check(a.snapshot().supplied() == c.snapshot().supplied(), "控制供能只收一次");
            ledger(c);
        }
        var clock =
                AssemblyContracts.line(
                        true, Route.Payload.SIGNAL, Rune.Kind.CLOCK, Rune.Kind.SIGNAL_SEND);
        a = new TickCircuit(clock);
        c = new TickCircuit(speed(clock, 8));
        a.start();
        c.start();
        p = supply();
        q = supply();
        for (int i = 0; i < 80; i++) {
            a.tick(p);
            c.tick(q);
            check(a.logic(1).equals(c.logic(1)) && a.signal(2) == c.signal(2), "时钟不因传输加速而改变频率");
        }
    }

    private static void recovery() {
        var b =
                AssemblyContracts.line(
                        true,
                        Route.Payload.MANA,
                        Rune.Kind.INPUT,
                        Rune.Kind.CONVERT,
                        Rune.Kind.OUTPUT);
        var c = new TickCircuit(b);
        c.start();
        var p = supply();
        c.tick(p);
        String before = JsonCodecs.snapshot(c);
        var next = c.withManaSteps(8);
        var old = c.snapshot();
        var now = next.snapshot();
        check(
                c.definition().rules().version() == 1 && next.definition().rules().version() == 3,
                "迁移不反写原实例");
        check(
                now.clock() == old.clock()
                        && now.powered() == old.powered()
                        && now.remaining() == old.remaining()
                        && now.injectionCountdown() == old.injectionCountdown(),
                "迁移保留生命周期和时间");
        check(
                Arrays.equals(now.transit(), old.transit())
                        && Arrays.equals(now.inbox(), old.inbox())
                        && Arrays.equals(now.pending(), old.pending())
                        && Arrays.equals(now.local(), old.local())
                        && now.logic().equals(old.logic())
                        && Arrays.equals(now.gates(), old.gates()),
                "迁移保留在途、缓冲、控制和计数状态");
        check(
                next.held() == c.held()
                        && now.supplied() == old.supplied()
                        && now.loss() == old.loss()
                        && now.delivered() == old.delivered(),
                "迁移不补充、不销毁魔力");
        check(next.withManaSteps(8) == next, "相同策略不重建VM");
        var copy = JsonCodecs.readSnapshot(JsonCodecs.snapshot(next));
        var q = JsonCodecs.readPool(JsonCodecs.pool(p), 0, 0);
        for (int i = 0; i < 50; i++) {
            check(next.tick(p).equals(copy.tick(q)), "高速现场恢复输出确定");
            check(JsonCodecs.snapshot(next).equals(JsonCodecs.snapshot(copy)), "高速完整现场确定");
            ledger(next);
        }
        check(JsonCodecs.snapshot(c).equals(before), "迁移和推进副本不会更改旧实例");
        var group = new CircuitAssembly(UUID.randomUUID(), AssemblyResources.rules(), c);
        group.add(b.rules());
        group.retimeMana(8);
        var saved = JsonCodecs.assembly(group);
        check(JsonCodecs.assembly(JsonCodecs.readAssembly(saved)).equals(saved), "多法阵快照兼容新速度");
        String text = JsonCodecs.snapshot(group.circuit(1));
        rejects(
                () ->
                        JsonCodecs.readSnapshot(
                                text.replaceFirst("\"version\":14", "\"version\":7")));
        rejects(() -> JsonCodecs.readSnapshot(text.replace(",\"manaSteps\":8", "")));
        rejects(() -> b.rules().withManaSteps(0));
        rejects(() -> b.rules().withManaSteps(65));
        check(
                JsonCodecs.readBlueprint(JsonCodecs.blueprint(b)).rules().manaSteps() == 1,
                "历史规则继续保留原节拍");
    }

    private static void cooldown() {
        var b =
                speed(
                        AssemblyContracts.line(
                                false,
                                Route.Payload.MANA,
                                Rune.Kind.INPUT,
                                Rune.Kind.CONVERT,
                                Rune.Kind.OUTPUT),
                        64);
        var c = new TickCircuit(b);
        var p = supply();
        c.start();
        for (int i = 0; i < 20; i++) {
            check(c.tick(p, () -> false).isEmpty(), "冷却期间保留输出");
            ledger(c);
        }
        long spent = p.snapshot().spent();
        check(
                c.held() > 0 && c.tick(p, () -> true).size() == 1 && p.snapshot().spent() == spent,
                "CD恢复后交付原魔力且不重新收费");
        ledger(c);
        var assembly =
                new CircuitAssembly(
                        UUID.randomUUID(), AssemblyResources.rules(), new TickCircuit(b));
        assembly.add(b.rules());
        assembly.replace(2, b);
        assembly.slots().forEach(id -> assembly.circuit(id).start());
        boolean[] ready = {true};
        ReleasePermit permit =
                () -> {
                    if (!ready[0]) return false;
                    ready[0] = false;
                    return true;
                };
        check(assembly.advance(supply(), 1, permit).size() == 1, "多槽和64轮传输共享同一个CD许可");
        ready[0] = true;
        check(assembly.advance(supply(), 1, permit).size() == 1, "另一槽等待后仍会输出");
    }

    private static void maximumPayment() {
        var r = RuleResources.defaults();
        r =
                new Rules(
                        3,
                        r.material(),
                        r.conversion(),
                        r.inversion(),
                        r.coupling(),
                        Fixed.MAX,
                        Fixed.MAX,
                        1,
                        120,
                        0,
                        r.playerCapacity(),
                        r.playerRegen(),
                        r.playerDelay(),
                        r.range(),
                        r.radius(),
                        r.routingRetention(),
                        r.oppositionRetention(),
                        r.boards(),
                        8);
        var base =
                AssemblyContracts.line(
                        false,
                        Route.Payload.MANA,
                        Rune.Kind.INPUT,
                        Rune.Kind.AMOUNT,
                        Rune.Kind.OUTPUT);
        long request = Fixed.MAX * 3 / 4;
        var runes = new ArrayList<>(base.runes());
        Rune amount = runes.get(1);
        runes.set(
                1,
                new Rune(
                        amount.id(),
                        amount.kind(),
                        amount.cell(),
                        amount.element(),
                        request,
                        amount.on(),
                        amount.signalControl(),
                        amount.weights()));
        var c = new TickCircuit(new Blueprint(5, r, runes, base.routes(), false));
        c.start();
        var p = supply();
        var output = c.tick(p);
        check(
                output.size() == 1 && output.getFirst().mana().total() == request && c.held() > 0,
                "上界部分支付的乘法不溢出，剩余留在原入口");
        ledger(c);
        check(
                JsonCodecs.snapshot(JsonCodecs.readSnapshot(JsonCodecs.snapshot(c)))
                        .equals(JsonCodecs.snapshot(c)),
                "大额部分支付现场完整恢复");
    }

    private static void feedbackAndField() {
        var rules = RuleResources.defaults();
        rules =
                new Rules(
                        1,
                        rules.material(),
                        rules.conversion(),
                        rules.inversion(),
                        rules.coupling(),
                        rules.nodeCapacity(),
                        rules.injection(),
                        rules.interval(),
                        rules.window(),
                        0,
                        rules.playerCapacity(),
                        rules.playerRegen(),
                        rules.playerDelay(),
                        rules.range(),
                        rules.radius(),
                        rules.routingRetention(),
                        rules.oppositionRetention());
        var in = Rune.create(1, Rune.Kind.INPUT, new Hex(-1, 0));
        var a = Rune.create(2, Rune.Kind.OR, new Hex(0, 0));
        var b = Rune.create(3, Rune.Kind.OR, new Hex(0, -1));
        var graph =
                new Blueprint(
                        5,
                        rules,
                        List.of(in, a, b),
                        List.of(
                                new Route(1, 2, Route.Payload.MANA, List.of(in.cell(), a.cell())),
                                new Route(2, 3, Route.Payload.MANA, List.of(a.cell(), b.cell())),
                                new Route(3, 2, Route.Payload.MANA, List.of(b.cell(), a.cell()))),
                        false);
        var slow = new TickCircuit(graph);
        var fast = new TickCircuit(speed(graph, 64));
        slow.start();
        fast.start();
        var p = supply();
        var q = supply();
        for (int i = 0; i < 64; i++) slow.tick(p);
        fast.tick(q);
        check(
                Arrays.equals(slow.snapshot().transit(), fast.snapshot().transit())
                        && Arrays.equals(slow.snapshot().inbox(), fast.snapshot().inbox())
                        && slow.snapshot().loss() == fast.snapshot().loss(),
                "反馈回路恰好推进64轮，取整和损耗不跳步");
        check(fast.snapshot().clock() == 1 && fast.held() > 0, "反馈不无限求值，也不吞掉剩余魔力");
        ledger(fast);
        var field =
                speed(
                        AssemblyContracts.line(
                                true,
                                Route.Payload.MANA,
                                Rune.Kind.ABSORB,
                                Rune.Kind.CONVERT,
                                Rune.Kind.OUTPUT),
                        64);
        var vm = new TickCircuit(field);
        vm.start();
        var ambient = new ManaPool(64 * Fixed.ONE, 0, 0);
        check(
                vm.tick(ManaSource.NONE, ReleasePermit.UNLIMITED, ambient).size() == 1
                        && ambient.snapshot().spent() == field.rules().injection(),
                "64轮只汲取一次场魔力");
        ledger(vm);
    }

    private static void routeArithmetic() {
        Random random = new Random(28);
        for (int trial = 0; trial < 240; trial++) {
            long[] values = new long[17];
            for (int i = 0; i < 17; i++) values[i] = random.nextInt(10000000);
            var mana = new Mixture(values);
            for (var material : Rules.Material.values()) {
                var r = RuleResources.defaults().withMaterial(material);
                int length = 1 + random.nextInt(300);
                var expected = mana.retain(r.coupling());
                for (int i = 0; i < length; i++) expected = expected.retain(r.retention());
                expected = expected.retain(r.coupling());
                check(
                        mana.transmit(r.coupling(), r.retention(), length).equals(expected),
                        "优化逐格取整必须逐成分完全相同");
            }
        }
        check(
                Mixture.pure(Fixed.MAX).transmit(Fixed.ONE, Fixed.ONE, 4095).total() == Fixed.MAX,
                "极值长线路无乘法溢出");
        check(Mixture.pure(1).transmit(Fixed.ONE, 65405, 4095).empty(), "耗尽后的提前退出与原算法相同");
    }

    public static int run() {
        timings();
        control();
        recovery();
        cooldown();
        maximumPayment();
        feedbackAndField();
        routeArithmetic();
        return checks;
    }
}
