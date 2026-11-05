package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.AssemblyResources;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.CircuitAddress;
import dev.elementalcircuits.domain.circuit.CircuitAssembly;
import dev.elementalcircuits.domain.circuit.CircuitLinks;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.ManaPool;

import java.util.*;

/** Real VM contracts for stable slots, causal transport, finite supply and exact recovery. */
public final class AssemblyContracts {
    private static int checks;

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    private static void rejects(Runnable action, String why) {
        boolean rejected = false;
        try {
            action.run();
        } catch (RuntimeException e) {
            rejected = true;
        }
        check(rejected, why);
    }

    public static Blueprint line(boolean continuous, Route.Payload payload, Rune.Kind... kinds) {
        List<Rune> nodes = new ArrayList<>();
        List<Route> routes = new ArrayList<>();
        for (int i = 0; i < kinds.length; i++)
            nodes.add(Rune.create(i + 1, kinds[i], new Hex(i - 2, 0)));
        for (int i = 1; i < kinds.length; i++)
            routes.add(
                    new Route(
                            i,
                            i + 1,
                            payload,
                            List.of(nodes.get(i - 1).cell(), nodes.get(i).cell())));
        return new Blueprint(5, RuleResources.defaults(), nodes, routes, continuous);
    }

    private static CircuitAssembly pair(Blueprint a, Blueprint b) {
        var result =
                new CircuitAssembly(
                        UUID.randomUUID(), AssemblyResources.rules(), new TickCircuit(a));
        result.add(b.rules());
        result.replace(2, b);
        return result;
    }

    private static CircuitAddress address(CircuitAssembly a, int slot, int rune) {
        return new CircuitAddress(a.identity(), slot, rune);
    }

    private static void link(CircuitAssembly a, int source, int target, Route.Payload payload) {
        a.links()
                .connect(
                        address(a, 1, source),
                        address(a, 2, target),
                        payload,
                        id -> id.equals(a.identity()) ? a : null);
    }

    private static ManaPool supply() {
        return new ManaPool(64 * Fixed.ONE, 0, 0);
    }

    private static void ledger(CircuitAssembly a, ManaPool p) {
        long total = 0;
        for (int id : a.slots()) {
            var c = a.circuit(id);
            var s = c.snapshot();
            check(s.supplied() == s.loss() + s.delivered() + c.held(), "每阵账本含收发口缓存守恒");
            total += s.loss() + c.held();
            if (c.definition().runes().stream().noneMatch(r -> r.kind() == Rune.Kind.MANA_SEND))
                total += s.delivered();
        }
        var graph = a.links().snapshot();
        long held =
                graph.connections().stream()
                        .filter(c -> c.transit() != null)
                        .mapToLong(c -> c.transit().mana().total())
                        .sum();
        check(graph.accepted() == graph.delivered() + graph.loss() + held, "互联运输账本守恒");
        check(total + graph.loss() + held == p.snapshot().spent(), "整组与有限供给守恒，不重复计算接口交付");
    }

    private static void slots() {
        var a =
                pair(
                        line(false, Route.Payload.MANA, Rune.Kind.INPUT, Rune.Kind.OUTPUT),
                        Blueprint.empty(
                                RuleResources.defaults().withMaterial(Rules.Material.CRYSTAL)));
        check(
                a.selected() == 2
                        && a.circuit(1).definition().rules().material() == Rules.Material.WOOD
                        && a.circuit(2).definition().rules().material() == Rules.Material.CRYSTAL,
                "每槽独立基板与选中项");
        a.add(RuleResources.defaults());
        a.add(RuleResources.defaults());
        String before = JsonCodecs.assembly(a);
        rejects(() -> a.add(RuleResources.defaults()), "最多四阵");
        check(before.equals(JsonCodecs.assembly(a)), "满槽拒绝不改计数");
        a.select(1);
        var pool = supply();
        a.circuit(1).start();
        a.tick(pool, 1);
        check(a.circuit(2).snapshot().clock() == 0 && a.circuit(2).held() == 0, "未启动槽位无免费魔力且时钟不推进");
        rejects(() -> a.select(99), "稳定编号不允许猜测槽位");
        check(JsonCodecs.readAssembly(JsonCodecs.assembly(a)).selected() == 1, "保存当前选择");
    }

    private static void manaAndRecovery() {
        var a =
                pair(
                        line(
                                false,
                                Route.Payload.MANA,
                                Rune.Kind.INPUT,
                                Rune.Kind.CONVERT,
                                Rune.Kind.MANA_SEND),
                        line(
                                true,
                                Route.Payload.MANA,
                                Rune.Kind.MANA_RECEIVE,
                                Rune.Kind.DIRECTED,
                                Rune.Kind.BEAM,
                                Rune.Kind.OUTPUT));
        link(a, 3, 1, Route.Payload.MANA);
        rejects(() -> a.replace(2, Blueprint.empty(RuleResources.defaults())), "禁止重刻仍被互联引用的槽位");
        a.circuit(1).start();
        a.circuit(2).start();
        var pool = supply();
        long outputs = 0;
        for (int i = 0; i < 40; i++) {
            if (i == 0) {
                a.tick(pool, 2);
                check(a.circuit(2).snapshot().supplied() == 0, "快杖的两个子步不能跨越世界互联边界");
            } else
                for (var out : a.tick(pool, 2)) {
                    outputs += out.cast().mana().total();
                    check(
                            out.slot() == 2
                                    && out.cast().mana().dominant() == Element.FIRE
                                    && out.cast().spell().shape() == SpellSpec.Shape.BEAM,
                            "接收阵继续修饰元素并真实输出");
                }
            ledger(a, pool);
            var restored = JsonCodecs.readAssembly(JsonCodecs.assembly(a));
            var p2 = JsonCodecs.readPool(JsonCodecs.pool(pool), 0, 0);
            check(JsonCodecs.assembly(restored).equals(JsonCodecs.assembly(a)), "每个世界边界完整往返含在途缓存");
            var clone = JsonCodecs.readAssembly(JsonCodecs.assembly(a));
            var p3 = JsonCodecs.readPool(JsonCodecs.pool(pool), 0, 0);
            check(
                    clone.tick(p3, 2).equals(restored.tick(p2, 2))
                            && JsonCodecs.assembly(clone).equals(JsonCodecs.assembly(restored)),
                    "恢复后同步子步和输出轨迹相同");
        }
        check(outputs > 0 && pool.snapshot().spent() == 4 * Fixed.ONE, "只消费源阵的真实注入");
        a.stopAll();
        a.links().disconnect(1, id -> a);
        a.replace(1, Blueprint.empty(RuleResources.defaults()));
        check(a.links().connections().isEmpty(), "空闲线路显式拆除后可重刻");
        String text = JsonCodecs.assembly(a);
        rejects(
                () -> JsonCodecs.readAssembly(text.replace("\"selected\":2", "\"selected\":99")),
                "非法选中项拒绝");
        rejects(
                () ->
                        JsonCodecs.readAssembly(
                                text.replace("\"supplied\":262144", "\"supplied\":262145")),
                "嵌套运行账本损坏拒绝");
    }

    private static void backpressure() {
        var a =
                pair(
                        line(true, Route.Payload.MANA, Rune.Kind.INPUT, Rune.Kind.MANA_SEND),
                        line(true, Route.Payload.MANA, Rune.Kind.MANA_RECEIVE));
        link(a, 2, 1, Route.Payload.MANA);
        var pool = supply();
        a.circuit(1).start();
        for (int i = 0; i < 15; i++) a.tick(pool, 1);
        check(
                a.links().connections().get(0).transit() != null && a.circuit(2).held() == 0,
                "接收阵未起动则运输包保留");
        var state = JsonCodecs.links(a.links());
        for (int i = 0; i < 10; i++) a.tick(pool, 1);
        check(state.equals(JsonCodecs.links(a.links())), "堵塞支路不能覆盖运输包");
        ledger(a, pool);
        a.stopAll();
        rejects(() -> a.links().disconnect(1, id -> a), "停供也不能销毁在途包");
        a.circuit(2).start();
        a.tick(pool, 1);
        check(a.circuit(2).held() > 0, "接收方手动起阵后可领取冻结包");
        ledger(a, pool);
    }

    private static void signals() {
        var a =
                pair(
                        line(true, Route.Payload.SIGNAL, Rune.Kind.SIGNAL, Rune.Kind.SIGNAL_SEND),
                        line(
                                true,
                                Route.Payload.SIGNAL,
                                Rune.Kind.SIGNAL_RECEIVE,
                                Rune.Kind.SIGNAL_SEND));
        link(a, 2, 1, Route.Payload.SIGNAL);
        var pool = supply();
        check(a.circuit(1).start() && a.circuit(2).start(), "纯信号阵可以手动起阵");
        a.tick(pool, 2);
        check(!a.circuit(2).signal(2), "逻辑子步不能绕过一世界步延迟");
        a.tick(pool, 2);
        check(a.circuit(2).signal(2), "信号传入接收阵");
        check(pool.snapshot().spent() > 0, "跨阵信号需要真实控制供能");
        a.circuit(1).stop();
        a.tick(pool, 2);
        check(!a.circuit(2).signal(2), "源阵停供立即撤销对外驱动");
        var empty = ManaPool.restore(new ManaPool.State(1, 64 * Fixed.ONE, 0, 0, 0, 0), 0, 0);
        a.tick(empty, 1);
        check(!a.circuit(2).powered() && !a.circuit(2).signal(2), "断供停止对外信号");
        var inverted =
                pair(
                        line(true, Route.Payload.SIGNAL, Rune.Kind.SIGNAL, Rune.Kind.SIGNAL_SEND),
                        line(
                                true,
                                Route.Payload.SIGNAL,
                                Rune.Kind.SIGNAL_RECEIVE,
                                Rune.Kind.NOT,
                                Rune.Kind.SIGNAL_SEND));
        link(inverted, 2, 1, Route.Payload.SIGNAL);
        inverted.slots().forEach(id -> inverted.circuit(id).start());
        var power = supply();
        for (int i = 0; i < 6; i++) inverted.tick(power, 2);
        check(
                inverted.circuit(2).validSignal(3) && !inverted.circuit(2).signal(3),
                "有效真信号经非门变成有效假");
        inverted.circuit(1).stop();
        for (int i = 0; i < 4; i++) inverted.tick(power, 2);
        check(
                !inverted.circuit(2).validSignal(3) && !inverted.circuit(2).signal(3),
                "失效不被非门当作假输入产生伪真");
        var falseSource =
                new TickCircuit(
                        line(
                                true,
                                Route.Payload.SIGNAL,
                                Rune.Kind.SIGNAL_RECEIVE,
                                Rune.Kind.NOT,
                                Rune.Kind.SIGNAL_SEND));
        falseSource.start();
        falseSource.drive(1, false);
        for (int i = 0; i < 3; i++) falseSource.tick(power);
        check(falseSource.validSignal(3) && falseSource.signal(3), "有效假独立于失效，可以正常经过非门");
        var oldTree =
                com.google.gson.JsonParser.parseString(
                                JsonCodecs.snapshot(
                                        new TickCircuit(
                                                line(
                                                        false,
                                                        Route.Payload.MANA,
                                                        Rune.Kind.INPUT,
                                                        Rune.Kind.OUTPUT))))
                        .getAsJsonObject();
        oldTree.addProperty("version", 1);
        oldTree.remove("arrivalSequence");
        String old = oldTree.toString();
        rejects(() -> JsonCodecs.readSnapshot(old), "旧VM现场不再做运行态迁移");
    }

    private static void fanoutAndQuota() {
        var a =
                pair(
                        line(false, Route.Payload.MANA, Rune.Kind.INPUT, Rune.Kind.MANA_SEND),
                        line(true, Route.Payload.MANA, Rune.Kind.MANA_RECEIVE, Rune.Kind.OUTPUT));
        a.add(RuleResources.defaults());
        a.replace(3, a.circuit(2).definition());
        link(a, 2, 1, Route.Payload.MANA);
        a.links().connect(address(a, 1, 2), address(a, 3, 1), Route.Payload.MANA, id -> a);
        a.slots().forEach(id -> a.circuit(id).start());
        var pool = supply();
        long[] outputs = new long[4];
        for (int t = 0; t < 30; t++) {
            for (var o : a.tick(pool, 1)) outputs[o.slot()] += o.cast().mana().total();
            ledger(a, pool);
        }
        check(outputs[2] > 0 && Math.abs(outputs[2] - outputs[3]) <= 1, "多接收逐成分均分而非复制");
        var b = pair(a.circuit(1).definition(), a.circuit(1).definition());
        var c = new CircuitLinks(AssemblyResources.rules());
        var resolver =
                (java.util.function.Function<UUID, CircuitAssembly>)
                        (id -> id.equals(a.identity()) ? a : b);
        a.stopAll();
        c.connect(address(b, 1, 2), address(a, 2, 1), Route.Payload.MANA, resolver);
        String before = JsonCodecs.links(c);
        rejects(
                () -> c.connect(address(b, 2, 2), address(a, 3, 1), Route.Payload.MANA, resolver),
                "每载体最多一个向外输出槽位");
        check(JsonCodecs.links(c).equals(before), "额度拒绝原子不变");
        rejects(
                () -> JsonCodecs.readLinks(before.replace("\"accepted\":0", "\"accepted\":1")),
                "运输账本不守恒拒绝");
    }

    public static int run() {
        slots();
        manaAndRecovery();
        backpressure();
        signals();
        fanoutAndQuota();
        return checks;
    }
}
