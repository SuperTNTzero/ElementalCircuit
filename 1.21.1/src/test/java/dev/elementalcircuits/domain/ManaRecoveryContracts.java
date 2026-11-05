package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.resources.ManaRecoveryResources;
import dev.elementalcircuits.domain.mana.ManaPool;

public final class ManaRecoveryContracts {
    public static int run() {
        int checks = 0;
        var r = ManaRecoveryResources.rules();
        if (r.duration() != 3600 || r.longDuration() != 9600 || r.strongDuration() != 1800)
            throw new AssertionError("药水持续时间");
        checks++;
        for (int level = 0; level < 256; level++) {
            for (long base : new long[] {0, 1, Fixed.ONE, 4 * Fixed.ONE, Fixed.MAX}) {
                long expected = Math.min(Fixed.MAX, base * (level + 2));
                if (r.rate(base, level) != expected) throw new AssertionError("等级、零恢复与安全饱和");
                checks++;
            }
        }
        var p = new ManaPool(64 * Fixed.ONE, 4 * Fixed.ONE, 40);
        p.debit(32 * Fixed.ONE);
        var before = p.snapshot();
        p.configureRecovery(r.rate(4 * Fixed.ONE, 0), 40);
        if (!p.snapshot().equals(before)) throw new AssertionError("药水不补能或清除等待");
        checks++;
        for (int i = 0; i < 40; i++) p.tick();
        if (p.available() != 32 * Fixed.ONE) throw new AssertionError("药水保留恢复等待");
        checks++;
        for (int i = 0; i < 20; i++) p.tick();
        if (p.available() != 40 * Fixed.ONE) throw new AssertionError("I级每秒恢复8");
        checks++;
        p.configureRecovery(4 * Fixed.ONE, 40);
        for (int i = 0; i < 20; i++) p.tick();
        if (p.available() != 44 * Fixed.ONE) throw new AssertionError("移除后恢复基础速度");
        checks++;
        var zero = r.withBonus(0);
        if (zero.rate(4 * Fixed.ONE, 255) != 4 * Fixed.ONE) throw new AssertionError("可配置关闭加成");
        checks++;
        for (int bad : new int[] {-1, 256}) {
            boolean rejected = false;
            try {
                r.rate(Fixed.ONE, bad);
            } catch (IllegalArgumentException e) {
                rejected = true;
            }
            if (!rejected) throw new AssertionError("非法等级");
            checks++;
        }
        return checks;
    }
}
