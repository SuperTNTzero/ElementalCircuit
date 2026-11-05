package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.AssemblyResources;
import dev.elementalcircuits.adapter.resources.CooldownResources;
import dev.elementalcircuits.domain.casting.CastCooldown;
import dev.elementalcircuits.domain.casting.ReleasePermit;
import dev.elementalcircuits.domain.circuit.CircuitAssembly;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.ManaPool;

import java.util.*;

public final class CooldownContracts {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void rejects(Runnable action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (RuntimeException expected) {
            rejected = true;
        }
        check(rejected, "非法冷却必须拒绝");
    }

    public static int run() {
        var rules = CooldownResources.rules();
        check(
                rules.ticks(Fixed.ONE) == 12
                        && rules.ticks(Fixed.ONE * 3 / 4) == 16
                        && rules.ticks(Fixed.ONE * 3 / 2) == 8,
                "部件速度反比决定冷却");
        check(rules.ticks(Fixed.ONE * 5 / 4) == 10, "间隔向上取整，不能超出速度预算");
        rejects(() -> new CastCooldown(5, 4));
        rejects(() -> new CastCooldown(-1, 12));
        rejects(() -> new CastCooldown.Rules(2, 12));
        rejects(() -> rules.ticks(0));
        CastCooldown c = CastCooldown.READY.release(rules, Fixed.ONE);
        for (int t = 0; t < 12; t++) {
            check(!c.ready(), "完整十二个固定步后才就绪");
            c = c.tick();
        }
        check(c.ready() && c.tick() == c, "空闲冷却不分配新状态");
        var board =
                AssemblyContracts.line(
                        false,
                        Route.Payload.MANA,
                        Rune.Kind.INPUT,
                        Rune.Kind.CONVERT,
                        Rune.Kind.OUTPUT);
        var vm = new TickCircuit(board);
        var supply = new ManaPool(64 * Fixed.ONE, 0, 0);
        vm.start();
        for (int t = 0; t < 18; t++) check(vm.tick(supply, () -> false).isEmpty(), "拒绝许可不输出");
        check(
                vm.held() > 0 && vm.snapshot().delivered() == 0 && vm.snapshot().clock() == 18,
                "等待期间保留魔力，逻辑时钟继续");
        check(vm.snapshot().supplied() == vm.held() + vm.snapshot().loss(), "等待账本守恒");
        var restored = JsonCodecs.readSnapshot(JsonCodecs.snapshot(vm));
        var outputs = restored.tick(supply, () -> true);
        check(
                outputs.size() == 1 && outputs.get(0).mana().dominant() == Element.FIRE,
                "保存恢复后就绪释放原来的元素");
        check(
                restored.held() == 0
                        && restored.snapshot().supplied()
                                == restored.snapshot().loss() + restored.snapshot().delivered(),
                "没有重复收费或丢失支付");
        var group =
                new CircuitAssembly(
                        UUID.randomUUID(), AssemblyResources.rules(), new TickCircuit(board));
        group.add(board.rules());
        group.replace(2, board);
        group.slots().forEach(id -> group.circuit(id).start());
        CastCooldown[] shared = {CastCooldown.READY};
        int releases = 0;
        boolean second = false;
        ReleasePermit permit =
                () -> {
                    if (!shared[0].ready()) return false;
                    shared[0] = shared[0].release(rules, Fixed.ONE);
                    return true;
                };
        for (int t = 0; t < 40; t++) {
            shared[0] = shared[0].tick();
            var result = group.advance(supply, 1, permit);
            check(result.size() <= 1, "多个槽位共享单个释放预算");
            releases += result.size();
            second |= result.stream().anyMatch(o -> o.slot() == 2);
            for (int slot : group.slots()) {
                var v = group.circuit(slot);
                check(
                        v.snapshot().supplied()
                                == v.held() + v.snapshot().loss() + v.snapshot().delivered(),
                        "共享冷却逐阵守恒");
            }
        }
        check(releases == 2 && second, "两个阵均能保留并交付一次，不把CD当作丢弃过滤器");
        var pure =
                new TickCircuit(
                        AssemblyContracts.line(
                                false, Route.Payload.MANA, Rune.Kind.INPUT, Rune.Kind.OUTPUT));
        pure.start();
        int[] acquired = {0};
        for (int t = 0; t < 10; t++)
            pure.tick(
                    supply,
                    () -> {
                        acquired[0]++;
                        return true;
                    });
        check(acquired[0] == 0, "纯净输出不消耗世界施法CD");
        return checks;
    }
}
