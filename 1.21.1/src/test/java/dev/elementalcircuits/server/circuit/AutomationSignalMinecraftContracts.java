package dev.elementalcircuits.server.circuit;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.block.CircuitBlockEntity;
import dev.elementalcircuits.domain.AssemblyContracts;
import dev.elementalcircuits.domain.circuit.*;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

import java.util.UUID;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class AutomationSignalMinecraftContracts {
    @GameTest(template = "empty")
    public static void nativeRedstoneSamplesThroughHost(GameTestHelper h) {
        var level = h.getLevel();
        var p =
                net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                        level,
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "redstone-test"));
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, ElementalCircuits.CIRCUIT_BLOCK.get().defaultBlockState());
        var b = (CircuitBlockEntity) level.getBlockEntity(pos);
        b.placed(new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get()), p);
        p.setPos(net.minecraft.world.phys.Vec3.atCenterOf(pos).add(0, 2, 0));
        b.assembly()
                .replace(
                        1,
                        AssemblyContracts.line(
                                true,
                                Route.Payload.SIGNAL,
                                Rune.Kind.REDSTONE,
                                Rune.Kind.SIGNAL_SEND));
        b.charge(p);
        var host = MinecraftCircuitHost.fixed(level, b);
        var c = host.assembly().circuit(1);
        c.start();
        level.setBlockAndUpdate(pos.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        for (int i = 0; i < 3; i++) {
            c.clearDrives();
            host.sampleExternalSignals();
            c.tick(b);
        }
        h.assertTrue(c.signal(2), "固定宿主将真实相邻红石读取为阵内信号");
        level.setBlockAndUpdate(pos.west(), Blocks.AIR.defaultBlockState());
        for (int i = 0; i < 3; i++) {
            c.clearDrives();
            host.sampleExternalSignals();
            c.tick(b);
        }
        h.assertTrue(c.validSignal(2) && !c.signal(2), "撤去红石后输出有效低电平");
        h.succeed();
    }
}
