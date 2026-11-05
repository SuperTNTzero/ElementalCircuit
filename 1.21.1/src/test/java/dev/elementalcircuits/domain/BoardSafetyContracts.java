package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.AssemblyResources;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.domain.casting.*;
import dev.elementalcircuits.domain.circuit.*;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.examples.StandardCircuit;
import dev.elementalcircuits.domain.mana.*;

import java.util.*;

/** Protection runs against real intake and owned storage, before the VM can emit a spell. */
public final class BoardSafetyContracts {
    private static int checks;
    private static final long F = Fixed.ONE;

    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
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

    public static BoardSafety policy() {
        var weights = new ArrayList<>(Collections.nCopies(17, 1.0));
        weights.set(0, .6);
        weights.set(Element.FIRE.component(false), 1.5);
        weights.set(Element.WATER.component(false), .6);
        return new BoardSafety(
                1,
                Map.of(
                        Rules.Material.WOOD, new BoardSafety.Profile(128 * F, 256),
                        Rules.Material.BRASS, new BoardSafety.Profile(512 * F, 1024),
                        Rules.Material.CRYSTAL, new BoardSafety.Profile(1280 * F, 4096)),
                4,
                .125,
                8,
                2,
                6,
                8,
                80,
                weights);
    }

    private static CircuitAssembly assembly(Blueprint b) {
        var a =
                new CircuitAssembly(
                        UUID.randomUUID(), AssemblyResources.rules(), new TickCircuit(b));
        a.prepareBoards(policy());
        return a;
    }

    private static Rules rules() {
        return RuleResources.defaults()
                .withMaterial(Rules.Material.WOOD)
                .withManaSteps(8)
                .withCasting(
                        new CastingRules(
                                2,
                                655,
                                131,
                                F,
                                Collections.nCopies(17, 58982L),
                                0,
                                0,
                                F,
                                StandardState.defaults()));
    }

    private static List<CircuitAssembly.Output> tick(CircuitAssembly a, ManaSource source) {
        return a.advance(
                source,
                1,
                ReleasePermit.UNLIMITED,
                ManaSource.NONE,
                CastContext.NEUTRAL,
                ShapeRules.protocolV1(),
                slot -> c -> !a.assessBoard(slot, policy(), false).tripped());
    }

    private static void ledger(TickCircuit c) {
        var s = c.snapshot();
        check(
                s.supplied() == s.loss() + s.delivered() + c.held() + s.castingLedger().refunded(),
                "熔断后账本守恒");
    }

    private static void equations() {
        var p = policy();
        for (var m : Rules.Material.values()) {
            var c = p.fresh(m, 0);
            long safe = p.profiles().get(m).capacity();
            var normal = p.assess(c, m, safe, 4 * F, false);
            check(normal.condition().spent() == F / 8 && !normal.tripped(), "安全承载边界只有基础磨损");
            var overload = p.assess(c, m, safe * 2, 4 * F, false);
            check(
                    overload.condition().spent() > normal.condition().spent()
                            && !overload.tripped(),
                    "超载平方加剧磨损");
            check(!p.assess(c, m, safe * 4 - 1, 0, false).meltdown(), "阈值前一固定点不熔毁");
            var melt = p.assess(c, m, safe * 4, 0, false);
            check(
                    melt.meltdown() && melt.condition().broken() && melt.condition().melted(),
                    "四倍阈值含等号");
            check(!p.assess(melt.condition(), m, safe * 4, 0, false).meltdown(), "损坏不重复爆炸");
            check(!p.assess(c, m, safe * 8, safe * 8, true).tripped(), "创造模式免磨损与熔毁");
        }
        var c = p.fresh(Rules.Material.WOOD, 100 * F);
        check(
                p.assess(c, Rules.Material.WOOD, 0, 100 * F, false).condition().spent() == 0,
                "迁移历史供给不补扣");
        for (int i = 1; i <= 32; i++)
            c = p.assess(c, Rules.Material.WOOD, 0, (100L + i) * F, false).condition();
        check(c.spent() == F, "小数磨损累计，非逐次取整免耗");
        var finalC = c;
        rejects(() -> p.assess(finalC, Rules.Material.WOOD, 0, 1, false), "供给游标不可回退");
        var fire = Mixture.pure(512 * F).convert(Element.FIRE, F);
        var water = Mixture.pure(512 * F).convert(Element.WATER, F);
        var bf = p.blast(Rules.Material.WOOD, 512 * F, fire);
        var bw = p.blast(Rules.Material.WOOD, 512 * F, water);
        check(
                Math.abs(bf.energy() - 1.5) < 1e-9
                        && bf.damage() > bw.damage()
                        && bf.radius() > bw.radius(),
                "元素烈度进入统一爆炸公式");
        var maximum = p.blast(Rules.Material.WOOD, Fixed.MAX, fire);
        check(maximum.radius() <= 8 && maximum.damage() <= 80, "极限输入也服从爆炸预算");
        rejects(() -> p.blast(Rules.Material.WOOD, 511 * F, fire), "未熔毁不能计算爆炸");
    }

    private static void runtime() {
        var rules = rules();
        var b = StandardCircuit.create(rules, false).withInputAmount(1, 512 * F);
        var a = assembly(b);
        var pool = new ManaPool(2048 * F, 0, 0);
        check(a.circuit(1).start(), "完整基板可起阵");
        var casts = new ArrayList<CircuitAssembly.Output>();
        for (int i = 0; i < 20 && !a.boardBroken(1); i++) casts.addAll(tick(a, pool));
        check(casts.isEmpty() && a.boardBroken(1) && a.board(1).melted(), "实际注入后、任何输出前熔毁");
        check(
                pool.snapshot().spent() == 512 * F
                        && a.circuit(1).manaLoad() == 0
                        && a.circuit(1).snapshot().delivered() == 0,
                "耗散实际输入不产生施法或返还");
        ledger(a.circuit(1));
        check(!a.circuit(1).start(), "损坏基板封锁所有直接起阵入口");
        var restored = JsonCodecs.readAssembly(JsonCodecs.assembly(a));
        var untouched = restored.changeStamp();
        rejects(() -> restored.add(rules), "恢复后尚未绑定安装策略时拒绝创建不完整耐久记录");
        check(restored.changeStamp().equals(untouched), "拒绝安装不改变槽位和现场");
        restored.prepareBoards(policy());
        check(
                restored.snapshot().version() == 4
                        && restored.board(1).equals(a.board(1))
                        && !restored.circuit(1).start(),
                "v4保存完整损坏现场");
        restored.replace(1, b);
        check(restored.boardBroken(1), "重复保存蓝图不能恢复耐久");
        int next = restored.add(rules);
        restored.addLayer(1, 4);
        check(!restored.boardBroken(next) && restored.boardBroken(1), "槽位独立，叠阵共享一张物理基板");
        restored.renewBoard(1, policy());
        check(
                !restored.boardBroken(1)
                        && restored.layers(1).state().nextId() == 3
                        && restored.circuit(1).definition().equals(b),
                "更换损坏基板保留主阵和叠阵");
        var poor = assembly(b);
        var low = new ManaPool(56 * F, 0, 0);
        poor.circuit(1).start();
        for (int i = 0; i < 20; i++) tick(poor, low);
        check(
                !poor.boardBroken(1) && poor.board(1).spent() == 0 && low.snapshot().spent() == 0,
                "魔力不足不按请求量虚构爆炸或磨损");
        var legacy =
                new CircuitAssembly(
                        UUID.randomUUID(), AssemblyResources.rules(), new TickCircuit(b));
        var loaded = JsonCodecs.readAssembly(JsonCodecs.assembly(legacy));
        loaded.prepareBoards(policy());
        check(loaded.snapshot().version() == 4 && loaded.board(1).spent() == 0, "旧版本显式安装全新健康记录");
        String bad = JsonCodecs.assembly(a).replace("\"maximum\":16777216", "\"maximum\":0");
        rejects(() -> JsonCodecs.readAssembly(bad), "坏耐久数据拒绝，不能补满");
    }

    private static void storedAndReceived() {
        var rules = rules();
        var input = Rune.create(1, Rune.Kind.INPUT, new Hex(-1, 0));
        var storage =
                new Rune(
                        2,
                        Rune.Kind.STORAGE,
                        new Hex(0, 0),
                        Element.PURE,
                        0,
                        true,
                        false,
                        List.of(),
                        new StorageSettings(2048 * F, 1024 * F, 1024 * F));
        var b =
                new Blueprint(
                                5,
                                rules,
                                List.of(input, storage),
                                List.of(
                                        new Route(
                                                1,
                                                2,
                                                Route.Payload.MANA,
                                                List.of(input.cell(), storage.cell()))),
                                false)
                        .withInputAmount(1, 300 * F);
        var a = assembly(b);
        var supply = new ManaPool(4096 * F, 0, 0);
        a.circuit(1).start();
        for (int i = 0; i < 20 && a.circuit(1).supplied() < 300 * F; i++) tick(a, supply);
        check(a.circuit(1).manaLoad() > 290 * F && !a.boardBroken(1), "单次中等过载可留在专用存储");
        a.circuit(1).stop();
        a.circuit(1).start();
        for (int i = 0; i < 20 && !a.boardBroken(1); i++) tick(a, supply);
        check(a.board(1).melted() && a.circuit(1).held() == 0, "多次小额注入累积至熔毁，缓存计入承载");
        ledger(a.circuit(1));
        var receiver = Rune.create(1, Rune.Kind.MANA_RECEIVE, new Hex(0, 0));
        var incoming = assembly(new Blueprint(5, rules, List.of(receiver), List.of(), false));
        incoming.circuit(1).start();
        check(
                incoming.circuit(1).receive(1, new Packet(Mixture.pure(600 * F), SpellSpec.EMPTY)),
                "互联接收实际魔力");
        tick(incoming, ManaSource.NONE);
        check(
                incoming.boardBroken(1)
                        && !incoming.circuit(1)
                                .receive(1, new Packet(Mixture.pure(F), SpellSpec.EMPTY)),
                "接收供能也经过熔断，损坏后拒绝新包");
        ledger(incoming.circuit(1));
        var absorber =
                assembly(
                        new Blueprint(
                                        5,
                                        rules,
                                        List.of(Rune.create(1, Rune.Kind.ABSORB, new Hex(0, 0))),
                                        List.of(),
                                        false)
                                .withInputAmount(1, 512 * F));
        var field = new ManaPool(1024 * F, 0, 0);
        absorber.circuit(1).start();
        absorber.advance(
                ManaSource.NONE,
                1,
                ReleasePermit.UNLIMITED,
                field,
                CastContext.NEUTRAL,
                ShapeRules.protocolV1(),
                slot -> c -> !absorber.assessBoard(slot, policy(), false).tripped());
        check(
                absorber.board(1).melted() && field.snapshot().spent() == 512 * F,
                "汲取口同样按实际魔力承载熔毁，不能绕过玩家输入保护");
        ledger(absorber.circuit(1));
        int healthy = incoming.add(rules);
        incoming.replace(healthy, StandardCircuit.create(rules, false));
        incoming.circuit(healthy).start();
        long output = 0;
        for (int i = 0; i < 12; i++)
            for (var cast : tick(incoming, new ManaPool(64 * F, 0, 0)))
                output += cast.cast().mana().total();
        check(
                output > 0 && incoming.boardBroken(1) && !incoming.boardBroken(healthy),
                "一张基板熔断不禁用其余健康槽位");
        var sample =
                BoardSafety.signature(StandardCircuit.create(rules, false), Mixture.pure(512 * F));
        check(
                sample.total() == 512 * F && sample.dominant() == Element.FIRE,
                "刚注入纯净魔力可根据相连元素刻印决定烈度");
    }

    public static int run() {
        equations();
        runtime();
        storedAndReceived();
        return checks;
    }
}
