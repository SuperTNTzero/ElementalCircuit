package dev.elementalcircuits.integration;

import dev.elementalcircuits.*;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.block.*;
import dev.elementalcircuits.domain.examples.StandardCircuit;
import dev.elementalcircuits.server.circuit.*;
import dev.elementalcircuits.server.mana.PlayerMana;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

import java.util.UUID;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class FixedControlMinecraftContracts {
    @GameTest(template = "empty")
    public static void nativeToolStartsSelectsAndCharges(GameTestHelper h) {
        var p =
                net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                        h.getLevel(),
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "control-test"));
        var pos = h.absolutePos(new BlockPos(2, 1, 2));
        h.getLevel()
                .setBlockAndUpdate(pos, ElementalCircuits.CIRCUIT_BLOCK.get().defaultBlockState());
        var b = (CircuitBlockEntity) h.getLevel().getBlockEntity(pos);
        b.placed(new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get()), p);
        p.setPos(Vec3.atCenterOf(pos).add(0, 0, -2));
        var a = b.assembly();
        var rules = ServerSettings.circuitRules();
        a.replace(1, StandardCircuit.create(rules, false));
        int second = a.add(rules);
        a.select(1);
        CarrierService.persist(b.carrier());
        var wand = new ItemStack(ElementalCircuits.CONTROL_WAND.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, wand);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
        p.gameMode.useItemOn(p, h.getLevel(), wand, InteractionHand.MAIN_HAND, hit);
        h.assertTrue(b.assembly().circuit(1).powered(), "工具实际右键启动固定阵");
        p.setShiftKeyDown(true);
        p.gameMode.useItemOn(p, h.getLevel(), wand, InteractionHand.MAIN_HAND, hit);
        h.assertTrue(
                b.assembly().selected() == second && b.assembly().circuit(1).powered(), "切槽保留旧阵运行");
        p.setShiftKeyDown(false);
        p.setItemInHand(
                InteractionHand.OFF_HAND, new ItemStack(ElementalCircuits.MANA_CRYSTAL.get()));
        PlayerMana.get(p).setAmount(PlayerMana.get(p).capacity());
        long before = PlayerMana.get(p).available(), reserve = b.available();
        p.gameMode.useItemOn(p, h.getLevel(), wand, InteractionHand.MAIN_HAND, hit);
        h.assertTrue(
                b.available() > reserve
                        && before - PlayerMana.get(p).available() == b.available() - reserve,
                "充能严格扣除玩家魔力");
        p.setPos(Vec3.atCenterOf(pos).add(20, 0, 0));
        boolean denied = false;
        try {
            FixedCircuitControl.apply(p, b, FixedCircuitControl.Action.TOGGLE);
        } catch (IllegalArgumentException e) {
            denied = true;
        }
        h.assertTrue(denied, "拒绝越距操作");
        h.succeed();
    }
}
