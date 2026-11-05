package dev.elementalcircuits.server.circuit;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.block.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.*;
import dev.elementalcircuits.domain.circuit.*;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.server.spell.WorldConnections;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

import java.util.UUID;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class ConnectionHostMinecraftContracts {
    @GameTest(template = "empty")
    public static void wandGestureStartsAndStopsConfiguredConnection(GameTestHelper h) {
        var level = h.getLevel();
        var player =
                net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                        level,
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "gesture-contact"));
        level.addNewPlayer(player);
        try {
            var wand = new ItemStack(ElementalCircuits.WAND.get());
            player.getInventory().selected = 0;
            player.getInventory().setItem(0, wand);
            var assembly = dev.elementalcircuits.adapter.persistence.CarrierRepository.load(wand);
            var blueprint =
                    dev.elementalcircuits.testing.TestBlueprints.entries().stream()
                            .filter(e -> e.file().equals("contact-constraint.ecblueprint"))
                            .findFirst()
                            .orElseThrow()
                            .blueprint();
            assembly.replace(1, blueprint);
            dev.elementalcircuits.adapter.persistence.CarrierRepository.commit(wand);
            dev.elementalcircuits.server.casting.WandCasting.begin(player, wand);
            dev.elementalcircuits.server.casting.WandCasting.release(player, wand);
            h.assertTrue(
                    assembly.circuit(1).powered()
                            && assembly.circuit(1).supplyMode() == SupplyMode.CONFIGURED,
                    "手持释放联系法阵保持显式持续供给配置");
            dev.elementalcircuits.server.casting.WandCasting.begin(player, wand);
            dev.elementalcircuits.server.casting.WandCasting.release(player, wand);
            h.assertTrue(!assembly.circuit(1).powered(), "再次右键停止，松手不误重启");
        } finally {
            dev.elementalcircuits.server.casting.WandCasting.cancel(player);
            level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fixedHostRoutesPureContactAndRevokesAtStop(GameTestHelper h) {
        var level = h.getLevel();
        var player =
                net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                        level,
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "host-contact"));
        level.addNewPlayer(player);
        player.getAbilities().mayBuild = true;
        var pos = h.absolutePos(new BlockPos(2, 3, 2));
        player.setPos(Vec3.atCenterOf(pos).add(-3, 1, 0));
        for (int x = -16; x <= 16; x += 16)
            for (int z = -16; z <= 16; z += 16) level.getChunkAt(pos.offset(x, 0, z));
        try {
            level.setBlockAndUpdate(
                    pos,
                    ElementalCircuits.CIRCUIT_BLOCK
                            .get()
                            .defaultBlockState()
                            .setValue(CircuitBlock.FACING, Direction.SOUTH));
            var block = (CircuitBlockEntity) level.getBlockEntity(pos);
            block.placed(new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get()), player);
            block.assembly()
                    .replace(
                            1,
                            AssemblyContracts.line(
                                    true,
                                    Route.Payload.MANA,
                                    Rune.Kind.INPUT,
                                    Rune.Kind.CONTACT,
                                    Rune.Kind.CONSTRAINT,
                                    Rune.Kind.OUTPUT));
            var host = MinecraftCircuitHost.fixed(level, block);
            var assembly = host.assembly();
            h.assertTrue(host.prepare() && assembly.circuit(1).start(), "原生固定宿主可启动联系法阵");
            var at = block.origin().add(0, -.125, 3);
            var item = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.STONE));
            item.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(item);
            var spec =
                    new SpellSpec(
                                    SpellSpec.Method.CONTACT,
                                    SpellSpec.Shape.UNSET,
                                    4 * Fixed.ONE,
                                    8,
                                    0,
                                    8192)
                            .withConnection(new ConnectionEffects(0, Fixed.ONE, false, false));
            host.emit(
                    new CircuitAssembly.Output(
                            1, new MagicCircuit.Cast(1, Mixture.pure(4 * Fixed.ONE), spec, 4)));
            host.commit();
            var data = WorldConnections.get(level);
            var key = new WorldConnections.Key(assembly.identity(), 1, 4);
            data.tick(level);
            h.assertTrue(item.getUUID().equals(data.target(key)), "宿主将纯净联系送入持久执行器，而非元素伤害入口");
            assembly.circuit(1).stop();
            host.commit();
            h.assertTrue(!data.contains(key), "宿主停阵提交时立即撤销联系权限和余额");
        } finally {
            level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
        h.succeed();
    }
}
