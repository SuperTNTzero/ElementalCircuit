package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.codec.BlueprintTextCode;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.ManaFieldResources;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.client.editor.EditorLayout;
import dev.elementalcircuits.client.editor.EditorViewport;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.logic.LogicDefaults;
import dev.elementalcircuits.domain.logic.RuneLogic;
import dev.elementalcircuits.domain.logic.RuneLogicState;
import dev.elementalcircuits.domain.mana.ManaField;
import dev.elementalcircuits.domain.mana.ManaFieldRules;
import dev.elementalcircuits.domain.mana.ManaGrowthRules;
import dev.elementalcircuits.domain.mana.ManaPool;
import dev.elementalcircuits.domain.mana.Mixture;

import java.util.*;

public final class ServerPolicyContracts {
    private static int checks;

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    private static void rejects(Runnable r) {
        boolean rejected = false;
        try {
            r.run();
        } catch (RuntimeException e) {
            rejected = true;
        }
        check(rejected, "非法状态须在发布前拒绝");
    }

    public static int run() {
        var legacy = RuleResources.defaults();
        var r = legacy.withBoards(new Rules.BoardSizes(32, 48, 64));
        check(
                legacy.boardRadius() == 4
                        && r.boardRadius() == 32
                        && r.withMaterial(Rules.Material.CRYSTAL).boardRadius() == 64,
                "新旧格盘独立冻结");
        List<Rune> nodes = new ArrayList<>();
        int id = 1025;
        for (int q = -20; q <= 20; q++)
            for (int t = -20; t <= 20; t++)
                if (new Hex(q, t).inside(20))
                    nodes.add(
                            new Rune(
                                    id++,
                                    Rune.Kind.CLOCK,
                                    new Hex(q, t),
                                    Element.PURE,
                                    2000000000L * Fixed.ONE,
                                    false,
                                    false,
                                    List.of()));
        var graph = new Blueprint(5, r, nodes, List.of(), true);
        var circuit = new TickCircuit(graph);
        circuit.start();
        var supply = new ManaPool(Fixed.MAX, 0, 0);
        for (int i = 0; i < 4; i++) circuit.tick(supply);
        var restored = JsonCodecs.readSnapshot(JsonCodecs.snapshot(circuit));
        check(JsonCodecs.snapshot(restored).equals(JsonCodecs.snapshot(circuit)), "大型现场恢复时钟");
        check(
                JsonCodecs.blueprint(restored.definition()).equals(JsonCodecs.blueprint(graph))
                        && restored.definition().runes().size() > 1000,
                "超过64符文、1024编号的蓝图完整恢复");
        check(
                JsonCodecs.readLogicObservation(JsonCodecs.logicObservation(circuit)).nodes().size()
                        == nodes.size(),
                "大型时序观察往返");
        check(BlueprintTextCode.decode(BlueprintTextCode.encode(graph)).equals(graph), "大型文本码压缩往返");
        var source = Rune.create(1, Rune.Kind.INPUT, new Hex(0, 0));
        var output = Rune.create(2, Rune.Kind.OUTPUT, new Hex(1, 0));
        var b =
                new Blueprint(
                                5,
                                legacy,
                                List.of(source, output),
                                List.of(
                                        new Route(
                                                1,
                                                2,
                                                Route.Payload.MANA,
                                                List.of(source.cell(), output.cell()))),
                                false)
                        .withInputAmount(1, Fixed.MAX);
        var vm = new TickCircuit(b);
        vm.start();
        var pool = new ManaPool(Fixed.MAX, 0, 0);
        vm.tick(pool);
        check(pool.snapshot().spent() == legacy.nodeCapacity(), "输入请求独立于玩家容量，注入按节点容量背压限流");
        check(
                JsonCodecs.readSnapshot(JsonCodecs.snapshot(vm)).snapshot().supplied()
                        == vm.snapshot().supplied(),
                "大请求账本保存恢复");
        check(
                b.withInputAmount(1, 1).inputAmount(b.withInputAmount(1, 1).rune(1)) == 1,
                "允许一个定点单位输入");
        check(
                SpellSpec.EMPTY
                                .modify(
                                        new Rune(
                                                1,
                                                Rune.Kind.AMOUNT,
                                                new Hex(0, 0),
                                                Element.PURE,
                                                Fixed.MAX,
                                                true,
                                                false,
                                                List.of()))
                                .amount()
                        == Fixed.MAX,
                "作用量不再限制64");
        var counter =
                new Rune(
                        1,
                        Rune.Kind.COUNTER,
                        new Hex(0, 0),
                        Element.PURE,
                        LogicDefaults.MAX_COUNT * Fixed.ONE,
                        true,
                        false,
                        List.of());
        var state = new RuneLogicState(LogicDefaults.MAX_COUNT - 1, 0, false, false, true);
        check(RuneLogic.step(counter, state, true, true, true).value(), "接近int边界的计数可正确触发并复位");
        var clock =
                new Rune(
                        1,
                        Rune.Kind.CLOCK,
                        new Hex(0, 0),
                        Element.PURE,
                        LogicDefaults.MAX_PERIOD * Fixed.ONE,
                        true,
                        false,
                        List.of());
        check(
                RuneLogic.step(
                                        clock,
                                        new RuneLogicState(
                                                0,
                                                LogicDefaults.MAX_PERIOD - 1,
                                                false,
                                                false,
                                                false),
                                        false,
                                        true,
                                        false)
                                .state()
                                .phase()
                        == 0,
                "极长节拍不溢出");
        var shares = Mixture.pure(Fixed.MAX).split(new int[] {1000000, 999999, 1});
        check(shares.stream().mapToLong(Mixture::total).sum() == Fixed.MAX, "大权重逐成分分配守恒");
        var p = new ManaPool(64 * Fixed.ONE, 4 * Fixed.ONE, 40);
        p.debit(20 * Fixed.ONE);
        long spent = p.snapshot().spent();
        p.setCapacity(2048 * Fixed.ONE);
        p.configureRecovery(8 * Fixed.ONE, 5);
        check(
                p.available() == 44 * Fixed.ONE
                        && p.snapshot().waitTicks() == 5
                        && p.snapshot().spent() == spent,
                "管理容量和恢复策略不补满或清空消费账本");
        p.setAmount(2000 * Fixed.ONE);
        p.setCapacity(1024 * Fixed.ONE);
        check(
                p.available() == 1024 * Fixed.ONE && p.snapshot().spent() == spent,
                "降低容量钳制余量，管理变动独立于消费");
        p.configureRecovery(0, 0);
        p.setAmount(0);
        for (int i = 0; i < 100; i++) p.tick();
        check(p.available() == 0, "零恢复确实停止恢复");
        rejects(() -> p.setCapacity(0));
        rejects(() -> p.setAmount(Fixed.MAX + 1));
        var fr = ManaFieldResources.defaults();
        var field = new ManaField(fr);
        var cell = new ManaField.Cell(0, 0, 0);
        field.discover(cell);
        field.inject(cell, fr.capacity() / 2);
        var before = field.snapshot();
        var next =
                new ManaFieldRules(
                        1,
                        8,
                        1,
                        fr.capacity() / 4,
                        0,
                        6,
                        65536,
                        fr.intakePerTick(),
                        fr.crystal(),
                        fr.naturalCapacity(),
                        fr.artificialCapacity(),
                        fr.naturalPerStep(),
                        fr.artificialPerStep());
        field.reconfigure(next);
        check(
                field.amount(cell) == next.capacity()
                        && field.snapshot().dissipated()
                                == before.dissipated()
                                        + before.cells().getFirst().amount()
                                        - next.capacity(),
                "降低场容量把超额记录为耗散");
        field.discover(new ManaField.Cell(1, 0, 0));
        check(
                field.amount(new ManaField.Cell(1, 0, 0)) == 0
                        && field.snapshot().ambientCreated() == before.ambientCreated(),
                "改变初生值只作用于新单元");
        var copy = ManaField.restore(field.snapshot());
        check(copy.snapshot().equals(field.snapshot()), "v2场保留变更前初生账本，恢复仍守恒");
        check(
                ManaField.restore(new ManaField(fr).snapshot()).snapshot().version() == 1,
                "旧场版本继续严格恢复");
        var camera = new EditorViewport();
        var layout = EditorLayout.of(1280, 800, 128);
        var target = new Hex(64, 0);
        for (int i = 0; i < 10; i++) camera.zoom(layout, layout.cx(), layout.cy(), 1);
        var view = camera.apply(layout);
        camera.pan(layout, layout.cx() - view.x(target), layout.cy() - view.y(target));
        view = camera.apply(layout);
        check(
                target.equals(view.cell(view.cx() + view.size() * Math.sqrt(3) * 64, view.cy())),
                "缩放平移后绘制与选格使用同一变换");
        camera.reset();
        check(camera.apply(layout).equals(layout), "视图复位不修改蓝图或尺寸");
        check(
                new ManaGrowthRules(2, 16 * Fixed.ONE, 2048 * Fixed.ONE).maximum()
                        == 2048 * Fixed.ONE,
                "显式新成长策略允许配置上限，旧协议不静默改写");
        return checks;
    }
}
