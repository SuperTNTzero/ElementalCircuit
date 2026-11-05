package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.ItemData;
import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.mana.ManaOrigin;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.server.equipment.WandEquipment;
import dev.elementalcircuits.server.mana.WorldManaField;
import dev.elementalcircuits.server.mana.WorldManaRefunds;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class QualityMinecraftContracts {
    @GameTest(template = "empty")
    public static void sectionIsolationAndAllocation(GameTestHelper h) {
        var stack = new ItemStack(ElementalCircuits.WAND.get());
        var root = new CompoundTag();
        root.putByteArray("UnrelatedCircuitPayload", new byte[1024 * 1024]);
        var section = new CompoundTag();
        section.putInt("Version", 1);
        section.putString("Head", "crystal_head");
        root.put("TestSection", section);
        ItemData.write(stack, root);
        var copy = ItemData.section(stack, "TestSection");
        copy.putInt("Version", 99);
        h.assertTrue(
                ItemData.section(stack, "TestSection").getInt("Version") == 1,
                "小节读取保持隔离，不泄漏组件可变引用");
        h.assertTrue(ItemData.section(stack, "Missing").isEmpty(), "缺失小节为空，读取不写物品");
        var bean =
                (com.sun.management.ThreadMXBean)
                        java.lang.management.ManagementFactory.getThreadMXBean();
        if (bean.isThreadAllocatedMemorySupported()) {
            bean.setThreadAllocatedMemoryEnabled(true);
            long thread = Thread.currentThread().threadId();
            for (int i = 0; i < 50; i++) ItemData.section(stack, "TestSection");
            long before = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 32; i++) ItemData.section(stack, "TestSection");
            long small = bean.getThreadAllocatedBytes(thread) - before;
            before = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 32; i++) ItemData.read(stack).getCompound("TestSection");
            long full = bean.getThreadAllocatedBytes(thread) - before;
            h.assertTrue(small * 10 < full, "查询小节不能复制无关的一MiB法阵载荷");
            org.slf4j.LoggerFactory.getLogger("QualityAudit")
                    .info(
                            "QUALITY_ALLOCATION: 32 reads section={} bytes, full={} bytes",
                            small,
                            full);
        }
        ItemData.put(stack, "WandAssembly", StringTag.valueOf("broken"));
        boolean rejected = false;
        try {
            WandEquipment.assembly(stack);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        h.assertTrue(
                rejected && ItemData.read(stack).getString("WandAssembly").equals("broken"),
                "错误部件根类型必须拒绝并保留原记录");
        h.succeed();
    }

    private static CompoundTag claim(String id, String origin, long amount, long paid) {
        var result = new CompoundTag();
        result.putString("Id", id);
        var source = new CompoundTag();
        source.putString("Origin", origin);
        source.putLong("Amount", amount);
        source.putLong("Paid", paid);
        var sources = new ListTag();
        sources.add(source);
        result.put("Sources", sources);
        return result;
    }

    @GameTest(template = "empty")
    public static void completedRefundsDoNotSpendWork(GameTestHelper h) {
        var level = h.getLevel();
        var field = WorldManaField.get(level);
        var cell = field.discover(level, h.absolutePos(new BlockPos(4, 2, 4)));
        field.field().debit(cell, field.field().amount(cell));
        String origin =
                "field/"
                        + level.dimension().location()
                        + "/"
                        + cell.x()
                        + ","
                        + cell.y()
                        + ","
                        + cell.z();
        var raw = new CompoundTag();
        raw.putInt("Version", 1);
        var claims = new ListTag();
        int history = ServerSettings.count("casting.refundWorkPerTick") * 2 + 1;
        for (int i = 0; i < history; i++)
            claims.add(claim("finished/" + i, origin, Fixed.ONE, Fixed.ONE));
        claims.add(claim("unfinished/0", origin, Fixed.ONE, 0));
        raw.put("Claims", claims);
        var service = WorldManaRefunds.load(raw);
        service.tick(level);
        h.assertTrue(
                service.pending() == 0 && field.field().amount(cell) == Fixed.ONE,
                "历史结清记录不挤占唯一待返还凭据的本步预算");
        var saved = service.save(new CompoundTag(), level.registryAccess());
        h.assertTrue(saved.getList("Claims", Tag.TAG_COMPOUND).size() == history + 1, "完成后保留防重复凭据");
        var restored = WorldManaRefunds.load(saved);
        restored.accept("unfinished/0", Mixture.pure(Fixed.ONE).sourced(new ManaOrigin(origin)));
        restored.tick(level);
        h.assertTrue(
                field.field().amount(cell) == Fixed.ONE && restored.pending() == 0,
                "保存恢复和重复交接不会再次入账");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void partialRefundKeepsUnpaidSources(GameTestHelper h) {
        String offline = "player/" + UUID.randomUUID();
        String finished = "player/" + UUID.randomUUID();
        var item = claim("mixed/0", finished, Fixed.ONE, Fixed.ONE);
        item.getList("Sources", Tag.TAG_COMPOUND)
                .add(
                        claim("mixed/0", offline, Fixed.ONE, 0)
                                .getList("Sources", Tag.TAG_COMPOUND)
                                .getCompound(0));
        var list = new ListTag();
        list.add(item);
        var raw = new CompoundTag();
        raw.putInt("Version", 1);
        raw.put("Claims", list);
        var outbox = WorldManaRefunds.load(raw);
        var mana =
                Mixture.pure(Fixed.ONE)
                        .sourced(new ManaOrigin(finished))
                        .plus(Mixture.pure(Fixed.ONE).sourced(new ManaOrigin(offline)));
        h.assertTrue(outbox.accept("mixed/0", mana), "恢复统一来源排序，重试不误判为凭据冲突");
        outbox.tick(h.getLevel());
        var restored =
                WorldManaRefunds.load(
                        outbox.save(new CompoundTag(), h.getLevel().registryAccess()));
        h.assertTrue(
                restored.pending() == Fixed.ONE && restored.accept("mixed/0", mana),
                "部分完成仍保留离线来源，历史完成部分不重新排队");
        h.succeed();
    }
}
