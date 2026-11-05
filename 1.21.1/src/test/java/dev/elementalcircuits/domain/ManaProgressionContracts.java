package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.codec.BlueprintTextCode;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.ManaGrowthResources;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.client.editor.BlueprintTransferModel;
import dev.elementalcircuits.client.editor.EditorDraft;
import dev.elementalcircuits.domain.casting.ReleasePermit;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.ManaGrowthRules;
import dev.elementalcircuits.domain.mana.ManaPool;
import dev.elementalcircuits.domain.mana.ManaSource;

import java.util.*;

public final class ManaProgressionContracts {
    private static int assertions;

    private static void check(boolean value, String text) {
        assertions++;
        if (!value) throw new AssertionError(text);
    }

    private static void rejects(Runnable action, String text) {
        boolean rejected = false;
        try {
            action.run();
        } catch (RuntimeException expected) {
            rejected = true;
        }
        check(rejected, text);
    }

    private static void ledger(TickCircuit vm) {
        var s = vm.snapshot();
        check(s.supplied() == vm.held() + s.loss() + s.delivered(), "可调输入仍严格守恒");
    }

    private static Rune source(int id, Hex h, long amount) {
        return new Rune(id, Rune.Kind.INPUT, h, Element.FIRE, amount, true, false, List.of(1));
    }

    private static void input() {
        var old =
                AssemblyContracts.line(
                        false, Route.Payload.MANA, Rune.Kind.INPUT, Rune.Kind.OUTPUT);
        var current =
                new Blueprint(
                        5,
                        old.rules(),
                        List.of(source(1, old.rune(1).cell(), 42 * Fixed.ONE), old.rune(2)),
                        old.routes(),
                        false);
        check(current.inputAmount(current.rune(1)) == 42 * Fixed.ONE, "当前蓝图按符文配置读取输入量");
        for (long value :
                new long[] {
                    Fixed.ONE / 4, Fixed.ONE / 2, Fixed.ONE, 8 * Fixed.ONE, 64 * Fixed.ONE
                }) {
            var b = current.withInputAmount(1, value);
            check(b.version() == 5 && b.inputAmount(b.rune(1)) == value, "每入口的独立数量");
            check(BlueprintTextCode.decode(BlueprintTextCode.encode(b)).equals(b), "文本码完整往返");
            var vm = new TickCircuit(b);
            var pool = new ManaPool(1024 * Fixed.ONE, 0, 0);
            vm.start();
            long output = 0;
            for (int tick = 0; tick < 45; tick++) {
                for (var cast : vm.tick(pool)) output += cast.mana().total();
                ledger(vm);
                var restored = JsonCodecs.readSnapshot(JsonCodecs.snapshot(vm));
                check(JsonCodecs.snapshot(restored).equals(JsonCodecs.snapshot(vm)), "每个边界的v7现场恢复");
                vm = restored;
            }
            check(
                    output > 0
                            && vm.snapshot().supplied() == value
                            && pool.available() == 1024 * Fixed.ONE - value,
                    "单次实际消费由参数决定");
            var d = new EditorDraft(b);
            d.place(Rune.Kind.SIGNAL, new Hex(2, 2));
            d.move(3, new Hex(2, 1));
            d.delete(3);
            check(
                    d.get().version() == 5 && d.get().inputAmount(d.get().rune(1)) == value,
                    "编辑操作保留当前蓝图与输入参数");
            d.undo();
            d.redo();
            d.delete(2);
            d.place(Rune.Kind.OUTPUT, b.rune(2).cell());
            d.route(
                    1,
                    2,
                    Route.Payload.MANA,
                    List.of(d.get().rune(1).cell(), d.get().rune(2).cell()));
        }
    }

    private static void lifecycle() {
        var b =
                new Blueprint(
                                5,
                                RuleResources.defaults(),
                                List.of(
                                        source(1, new Hex(-1, 0), 123),
                                        source(2, new Hex(1, 0), 456)),
                                List.of(),
                                true)
                        .withInputAmount(1, 8 * Fixed.ONE);
        check(b.rune(2).value() == 456 && b.inputAmount(b.rune(2)) == 456, "修改一个入口不会覆盖其他入口参数");
        b = b.withInputAmount(2, Fixed.ONE / 2);
        var vm = new TickCircuit(b);
        var shortage = new ManaPool(8 * Fixed.ONE, 0, 0);
        vm.start();
        vm.tick(shortage);
        check(
                !vm.powered() && vm.held() == 0 && shortage.available() == 8 * Fixed.ONE,
                "多入口总报价不足原子失败");
        vm = new TickCircuit(b);
        var pool = new ManaPool(1024 * Fixed.ONE, 0, 0);
        vm.start();
        vm.tick(pool);
        check(vm.held() == 8 * Fixed.ONE + Fixed.ONE / 2, "多入口按各自金额扣款");
        ledger(vm);
        for (int i = 0; i < 220; i++) {
            vm.tick(pool);
            ledger(vm);
        }
        long spent = pool.snapshot().spent();
        for (int i = 0; i < 30; i++) vm.tick(pool);
        check(pool.snapshot().spent() == spent && vm.powered(), "任一入口缓存满时全部入口背压，不偷扣魔力");
        vm.stop();
        var recovery =
                new TickCircuit(
                        new Blueprint(
                                5,
                                b.rules(),
                                List.of(source(1, new Hex(0, 0), 8 * Fixed.ONE)),
                                List.of(),
                                false));
        recovery.start();
        recovery.tick(pool);
        recovery.stop();
        long residual = recovery.held();
        var replacement = recovery.reconfigure(recovery.definition().withInputAmount(1, Fixed.ONE));
        var before = pool.snapshot();
        replacement.start();
        replacement.tick(pool);
        check(
                pool.snapshot().spent() == before.spent() && replacement.held() == residual,
                "重配优先复用真实回收量，无重复收费");
        ledger(replacement);
        var transfer = new BlueprintTransferModel();
        transfer.input = BlueprintTextCode.encode(b);
        var imported = transfer.imported(b.rules().withMaterial(Rules.Material.CRYSTAL));
        check(
                imported.version() == 5 && imported.inputAmount(imported.rune(2)) == Fixed.ONE / 2,
                "导入绑定载体且保留当前蓝图");
        rejects(
                () -> new Blueprint(6, RuleResources.defaults(), List.of(), List.of(), false),
                "拒绝未知蓝图版本");
        final var board = b;
        rejects(() -> board.withInputAmount(1, 0), "显式设置拒绝零量");
        rejects(() -> board.withInputAmount(1, -1), "拒绝过小输入");
        rejects(() -> board.withInputAmount(1, Fixed.MAX + 1), "拒绝超缓存上限输入");
        var modern = new TickCircuit(b);
        rejects(
                () ->
                        JsonCodecs.readSnapshot(
                                JsonCodecs.snapshot(modern)
                                        .replaceFirst("\"version\":14", "\"version\":6")),
                "当前现场协议拒绝旧版伪装");
        var ambientRune =
                new Rune(
                        1,
                        Rune.Kind.ABSORB,
                        new Hex(0, 0),
                        Element.FIRE,
                        0,
                        true,
                        false,
                        List.of(1));
        var ambientBoard =
                new Blueprint(5, board.rules(), List.of(ambientRune), List.of(), true)
                        .withInputAmount(1, 2 * Fixed.ONE);
        var ambientVm = new TickCircuit(ambientBoard);
        ambientVm.start();
        var field = new ManaPool(1024 * Fixed.ONE, 0, 0);
        ambientVm.tick(ManaSource.NONE, ReleasePermit.UNLIMITED, field);
        check(ambientVm.held() == 2 * Fixed.ONE, "环境入口也可调节汲取上限");
        ledger(ambientVm);
    }

    private static void growth() {
        var rules = ManaGrowthResources.rules();
        var p = new ManaPool(64 * Fixed.ONE, 4 * Fixed.ONE, 40);
        p.debit(10 * Fixed.ONE);
        var before = p.snapshot();
        check(rules.apply(p) && p.capacity() == 80 * Fixed.ONE, "果实永久增加16容量");
        check(
                p.available() == before.amount()
                        && p.snapshot().waitTicks() == 40
                        && p.snapshot().spent() == before.spent(),
                "果实不补能、不清恢复等待或账本");
        for (int i = 1; i < 60; i++) {
            check(rules.apply(p), "连续容量成长");
            var loaded = JsonCodecs.readPool(JsonCodecs.pool(p), 4 * Fixed.ONE, 40);
            check(loaded.snapshot().equals(p.snapshot()), "升级容量完整保存恢复");
            p = loaded;
        }
        check(p.capacity() == 1024 * Fixed.ONE && !rules.apply(p), "60颗从64到1024，到顶拒绝");
        for (int i = 0; i < 6000; i++) p.tick();
        check(p.available() == 1024 * Fixed.ONE, "新增容量由原有恢复补足");
        var near = new ManaPool(1020 * Fixed.ONE, 0, 0);
        check(
                rules.apply(near)
                        && near.capacity() == 1024 * Fixed.ONE
                        && near.available() == 1020 * Fixed.ONE,
                "历史非整档容量最终钳制1024");
        final var capped = p;
        var state = p.snapshot();
        check(!rules.apply(p) && p.snapshot().equals(state), "满级重复使用无状态变化");
        rejects(() -> capped.increaseCapacity(-1, 1024 * Fixed.ONE), "拒绝负成长");
        rejects(() -> new ManaGrowthRules(1, 16 * Fixed.ONE, 2048 * Fixed.ONE), "成长规则不能放宽1024硬上限");
    }

    public static int run() {
        assertions = 0;
        input();
        lifecycle();
        growth();
        return assertions;
    }
}
