package dev.elementalcircuits.domain;

import com.google.gson.*;

import dev.elementalcircuits.adapter.codec.BlueprintTextCode;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.LogicResources;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.examples.LogicExamples;
import dev.elementalcircuits.domain.logic.LogicDefaults;
import dev.elementalcircuits.domain.logic.RuneLogic;
import dev.elementalcircuits.domain.logic.RuneLogicState;
import dev.elementalcircuits.domain.mana.ManaPool;

import java.nio.file.*;
import java.util.*;

public final class LogicContracts {
    private static int checks;

    private static void check(boolean ok, String text) {
        checks++;
        if (!ok) throw new AssertionError(text);
    }

    private static void rejects(Runnable action, String text) {
        boolean bad = false;
        try {
            action.run();
        } catch (RuntimeException e) {
            bad = true;
        }
        check(bad, text);
    }

    private static Rune parameter(Rune.Kind kind, int value) {
        Rune r = Rune.create(1, kind, new Hex(0, 0));
        return new Rune(
                r.id(),
                r.kind(),
                r.cell(),
                r.element(),
                value * Fixed.ONE,
                r.on(),
                false,
                r.weights());
    }

    private static ManaPool pool() {
        return new ManaPool(1024 * Fixed.ONE, 0, 0);
    }

    private static void rules() {
        check(LogicResources.defaults().equals(LogicDefaults.protocolV1()), "创建默认值资源明确匹配协议初值");
        for (Rune.Kind kind : List.of(Rune.Kind.COUNTER, Rune.Kind.CLOCK, Rune.Kind.TOGGLE)) {
            check(
                    kind.signalIn() && kind.signalOut() && !kind.manaIn() && !kind.manaOut(),
                    "时序只接信号，不隐式创造魔力");
            check(!RuneShapes.of(kind).isEmpty(), "新符文具备统一预览形状");
            check(RuneCatalog.Group.LOGIC.children.contains(kind), "目录可选择新符文");
        }
        check(
                Rune.create(1, Rune.Kind.COUNTER, new Hex(0, 0), new LogicDefaults(1, 9, 31))
                                .value()
                        == 9 * Fixed.ONE,
                "创建参数可注入，冻结在符文配置");
        rejects(() -> parameter(Rune.Kind.COUNTER, 0), "拒绝零阈值");
        rejects(() -> parameter(Rune.Kind.COUNTER, Integer.MAX_VALUE), "阈值有界");
        rejects(() -> parameter(Rune.Kind.CLOCK, 1), "周期至少两步，保证脉冲间有低电平");
        rejects(() -> parameter(Rune.Kind.CLOCK, Integer.MAX_VALUE), "周期有界");
        rejects(
                () ->
                        new Rune(
                                1,
                                Rune.Kind.COUNTER,
                                new Hex(0, 0),
                                Element.FIRE,
                                Fixed.ONE + 1,
                                false,
                                false,
                                List.of(1)),
                "拒绝小数计数阈值");
        rejects(
                () ->
                        new Rune(
                                1,
                                Rune.Kind.CLOCK,
                                new Hex(0, 0),
                                Element.FIRE,
                                2 * Fixed.ONE + 1,
                                false,
                                false,
                                List.of(1)),
                "拒绝小数逻辑步");
    }

    private static void transitions() {
        Rune counter = parameter(Rune.Kind.COUNTER, 3);
        var state = RuneLogicState.initial(counter);
        state = RuneLogic.step(counter, state, false, true, true).state();
        state = RuneLogic.step(counter, state, true, true, true).state();
        check(state.count() == 1, "首次上升沿计一");
        for (int i = 0; i < 20; i++) {
            var r = RuneLogic.step(counter, state, true, true, true);
            state = r.state();
            check(state.count() == 1 && !r.value(), "长高电平不重计");
        }
        state = RuneLogic.step(counter, state, false, true, true).state();
        state = RuneLogic.step(counter, state, true, true, true).state();
        check(state.count() == 2, "第二次上升沿");
        var invalid = RuneLogic.step(counter, state, false, false, true);
        check(invalid.state().count() == 2 && !invalid.valid(), "无效输入暂停计数");
        state = RuneLogic.step(counter, invalid.state(), true, true, true).state();
        check(state.count() == 2, "恢复高电平先同步，无伪边沿");
        state = RuneLogic.step(counter, state, false, true, true).state();
        var fired = RuneLogic.step(counter, state, true, true, true);
        check(fired.value() && fired.state().count() == 0, "第N次发单步脉冲并循环归零");
        check(!RuneLogic.step(counter, fired.state(), true, true, true).value(), "脉冲仅一步");
        Rune clock = parameter(Rune.Kind.CLOCK, 5);
        state = RuneLogicState.EMPTY;
        for (int i = 1; i <= 30; i++) {
            var r = RuneLogic.step(clock, state, false, true, false);
            state = r.state();
            check(r.value() == (i % 5 == 0), "无输入时精确周期脉冲");
        }
        state = RuneLogic.step(clock, state, true, true, true).state();
        int phase = state.phase();
        for (int i = 0; i < 9; i++) state = RuneLogic.step(clock, state, false, true, true).state();
        check(state.phase() == phase, "假使能暂停相位");
        var paused = RuneLogic.step(clock, state, true, false, true);
        check(!paused.valid() && paused.state().phase() == phase, "失效使能也冻结相位");
        Rune toggle = Rune.create(1, Rune.Kind.TOGGLE, new Hex(0, 0));
        state = RuneLogicState.initial(toggle);
        check(!state.latched(), "锁存默认关");
        state = RuneLogic.step(toggle, state, false, true, true).state();
        state = RuneLogic.step(toggle, state, true, true, true).state();
        check(state.latched(), "一次上升沿切开");
        state = RuneLogic.step(toggle, state, true, true, true).state();
        check(state.latched(), "持续高保持");
        state = RuneLogic.step(toggle, state, false, true, true).state();
        state = RuneLogic.step(toggle, state, true, true, true).state();
        check(!state.latched(), "第二次上升沿切关");
    }

    private static final class Bench {
        TickCircuit circuit;
        ManaPool supply = pool();

        Bench(Rune.Kind kind) {
            circuit =
                    new TickCircuit(
                            AssemblyContracts.line(
                                    true,
                                    Route.Payload.SIGNAL,
                                    Rune.Kind.SIGNAL_RECEIVE,
                                    kind,
                                    Rune.Kind.SIGNAL_SEND));
            circuit.start();
        }

        void step(boolean high, boolean valid) {
            circuit.clearDrives();
            if (valid) circuit.drive(1, high);
            circuit.tick(supply);
        }

        void settle(boolean high, boolean valid) {
            for (int i = 0; i < 4; i++) step(high, valid);
        }
    }

    private static void lifecycle() {
        var b = new Bench(Rune.Kind.COUNTER);
        b.settle(false, true);
        b.settle(true, true);
        check(b.circuit.logic(2).count() == 1, "真实VM逐线计一次");
        long spent = b.supply.snapshot().spent();
        check(
                spent == 8 * 3 * RuleResources.defaults().signalCost()
                        && b.circuit.snapshot().loss() == spent,
                "三个控制符文逐步真实供能并守恒");
        b.circuit.resetLogic(2);
        b.settle(true, true);
        check(b.circuit.logic(2).count() == 0, "高电平复位不制造边沿");
        b.settle(false, true);
        b.settle(true, true);
        b.circuit.stop();
        var before = b.circuit.logic(2);
        for (int i = 0; i < 5; i++) b.step(false, false);
        check(b.circuit.logic(2).equals(before) && !b.circuit.validSignal(3), "停供冻结计数并撤销有效输出");
        b.circuit.start();
        b.settle(true, true);
        check(b.circuit.logic(2).count() == 1, "重启高电平只同步，保留计数");
        b.settle(false, true);
        b.settle(true, true);
        check(b.circuit.logic(2).count() == 2, "重启后新的上升沿正常计数");
        b.supply = ManaPool.restore(new ManaPool.State(1, 64 * Fixed.ONE, 0, 0, 0, 0), 0, 0);
        b.step(false, true);
        check(!b.circuit.powered() && b.circuit.logic(2).count() == 2, "断供不清计数");
        b.supply = pool();
        b.step(true, true);
        check(!b.circuit.powered(), "补能不自动起阵");
        rejects(() -> b.circuit.resetLogic(1), "不能复位非时序符文");
        b.circuit.stop();
        TickCircuit fresh = b.circuit.reconfigure(b.circuit.definition());
        check(fresh.logic(2).count() == 0, "重刻新配置重置逻辑态，不伪装继续运行");
        var clock =
                new TickCircuit(
                        new Blueprint(
                                5,
                                RuleResources.defaults(),
                                List.of(parameter(Rune.Kind.CLOCK, 5)),
                                List.of(),
                                true));
        clock.start();
        for (int i = 0; i < 3; i++) clock.tick(pool());
        clock.stop();
        clock.start();
        clock.tick(pool());
        check(clock.logic(1).phase() == 4, "停供重启保留节拍相位");
        clock.resetLogic(1);
        check(clock.logic(1).phase() == 0, "显式复位相位归零");
    }

    private static void recovery() {
        var b = new Bench(Rune.Kind.TOGGLE);
        Random random = new Random(709);
        for (int t = 0; t < 400; t++) {
            boolean high = random.nextBoolean(), valid = random.nextInt(5) != 0;
            b.step(high, valid);
            if (t % 37 == 0) b.circuit.resetLogic(2);
            String text = JsonCodecs.snapshot(b.circuit);
            TickCircuit restored = JsonCodecs.readSnapshot(text);
            check(JsonCodecs.snapshot(restored).equals(text), "每个边界包括采样基线完整往返");
            var supply = JsonCodecs.readPool(JsonCodecs.pool(b.supply), 0, 0);
            TickCircuit clone = TickCircuit.restore(b.circuit.snapshot());
            var p2 = JsonCodecs.readPool(JsonCodecs.pool(b.supply), 0, 0);
            restored.clearDrives();
            clone.clearDrives();
            if (valid) {
                restored.drive(1, high);
                clone.drive(1, high);
            }
            check(
                    restored.tick(supply).equals(clone.tick(p2))
                            && JsonCodecs.snapshot(restored).equals(JsonCodecs.snapshot(clone)),
                    "保存后同输入得到相同逻辑轨迹");
        }
        var root = JsonParser.parseString(JsonCodecs.snapshot(b.circuit)).getAsJsonObject();
        var invalid = root.deepCopy();
        invalid.getAsJsonArray("logic").get(1).getAsJsonObject().addProperty("count", 1);
        rejects(() -> JsonCodecs.readSnapshot(invalid.toString()), "锁存不能藏计数状态");
        var missing = root.deepCopy();
        missing.remove("logic");
        rejects(() -> JsonCodecs.readSnapshot(missing.toString()), "新版本缺逻辑态不能免费初始化");
        var old =
                new TickCircuit(
                        AssemblyContracts.line(
                                true,
                                Route.Payload.SIGNAL,
                                Rune.Kind.SIGNAL,
                                Rune.Kind.SIGNAL_SEND));
        old.start();
        old.tick(pool());
        var legacy = JsonParser.parseString(JsonCodecs.snapshot(old)).getAsJsonObject();
        legacy.addProperty("version", 2);
        legacy.remove("logic");
        legacy.remove("arrivalSequence");
        rejects(() -> JsonCodecs.readSnapshot(legacy.toString()), "开发版只接受完整的当前现场协议");
        var fakeOld = root.deepCopy();
        fakeOld.addProperty("version", 2);
        rejects(() -> JsonCodecs.readSnapshot(fakeOld.toString()), "旧协议不能偷渡新符文");
        var counter = new Bench(Rune.Kind.COUNTER);
        var badCounter =
                JsonParser.parseString(JsonCodecs.snapshot(counter.circuit)).getAsJsonObject();
        badCounter.getAsJsonArray("logic").get(1).getAsJsonObject().addProperty("count", 4);
        rejects(() -> JsonCodecs.readSnapshot(badCounter.toString()), "计数不能超过当前阈值");
    }

    private static void example() {
        var board = LogicExamples.alternating(RuleResources.defaults(), LogicResources.defaults());
        check(
                BlueprintTextCode.decode(BlueprintTextCode.encode(board)).equals(board),
                "完整示例分享文本码往返");
        TickCircuit c = new TickCircuit(board);
        var supply = pool();
        c.start();
        int fire = 0, water = 0, changes = 0;
        boolean previous = false;
        for (int i = 0; i < 330; i++) {
            for (var cast : c.tick(supply)) {
                if (cast.mana().dominant() == Element.FIRE) fire++;
                if (cast.mana().dominant() == Element.WATER) water++;
                check(cast.spell().shape() == SpellSpec.Shape.BEAM, "计数切换示例保留具体光束形状");
            }
            boolean now = c.logic(3).latched();
            if (now != previous) {
                changes++;
                previous = now;
            }
            var s = c.snapshot();
            check(s.supplied() == s.loss() + s.delivered() + c.held(), "自动切换仍严格守恒");
            if (i == 145) {
                var resumed = JsonCodecs.readSnapshot(JsonCodecs.snapshot(c));
                check(
                        resumed.logic(2).equals(c.logic(2)) && resumed.logic(3).equals(c.logic(3)),
                        "中途保存不会重新从火系开始");
                c = resumed;
            }
        }
        check(fire > 0 && water > 0 && changes == 4, "真实VM由三种新符文自动交替火水四次");
        try {
            Files.createDirectories(Path.of("build/logic-examples"));
            Files.writeString(
                    Path.of("build/logic-examples/计数切换.ec1.txt"), BlueprintTextCode.encode(board));
            Files.writeString(
                    Path.of("build/logic-examples/计数切换.json"), JsonCodecs.blueprint(board));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static int run() {
        rules();
        transitions();
        lifecycle();
        recovery();
        example();
        return checks;
    }
}
