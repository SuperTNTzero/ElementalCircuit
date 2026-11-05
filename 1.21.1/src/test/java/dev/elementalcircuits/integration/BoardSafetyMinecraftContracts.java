package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.circuit.*;
import dev.elementalcircuits.domain.examples.StandardCircuit;
import dev.elementalcircuits.server.circuit.*;
import dev.elementalcircuits.server.mana.PlayerMana;
import dev.elementalcircuits.server.spell.BoardMeltdown;
import dev.elementalcircuits.server.workshop.InscriptionAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

import java.util.UUID;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class BoardSafetyMinecraftContracts {
    private static final long F = Fixed.ONE;

    private static ServerPlayer player(GameTestHelper h) {
        var p =
                net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                        h.getLevel(),
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "board-test"));
        p.setPos(h.absoluteVec(new Vec3(2.5, 2, 2.5)));
        p.getAbilities().instabuild = false;
        p.getAbilities().invulnerable = false;
        p.getInventory().selected = 0;
        PlayerMana.get(p).setCapacity(2048 * F);
        PlayerMana.get(p).setAmount(2048 * F);
        return p;
    }

    private static ItemStack wand(ServerPlayer p, long input) {
        var wand = new ItemStack(ElementalCircuits.WAND.get());
        p.getInventory().setItem(0, wand);
        var a = CarrierRepository.load(wand);
        a.replace(
                1,
                StandardCircuit.create(
                                ServerSettings.circuitRules().withMaterial(Rules.Material.WOOD),
                                false)
                        .withInputAmount(1, input));
        CarrierRepository.commit(wand);
        return wand;
    }

    private static void advance(GameTestHelper h, ServerPlayer p, ItemStack stack) {
        for (int i = 0; i < 20; i++) {
            WorldCircuits.enqueue(stack, p);
            WorldCircuits.tick(h.getLevel());
        }
    }

    @GameTest(template = "empty")
    public static void actualMeltdownPersistsAndKeepsStaff(GameTestHelper h) {
        var p = player(h);
        var wand = wand(p, 512 * F);
        var a = CarrierRepository.load(wand);
        a.circuit(1).start();
        advance(h, p, wand);
        h.assertTrue(
                a.board(1).melted() && a.circuit(1).manaLoad() == 0 && !a.circuit(1).powered(),
                "真实调度器熔毁并清空魔力");
        h.assertTrue(
                wand.getCount() == 1
                        && wand.getDamageValue() == 0
                        && a.circuit(1).snapshot().delivered() == 0,
                "失败先于输出，保留杖体及设计");
        var encoded =
                (net.minecraft.nbt.CompoundTag)
                        wand.save(
                                h.getLevel().registryAccess(), new net.minecraft.nbt.CompoundTag());
        var restored =
                CarrierRepository.load(
                        ItemStack.parseOptional(h.getLevel().registryAccess(), encoded));
        h.assertTrue(
                restored.board(1).equals(a.board(1)) && !restored.circuit(1).start(),
                "原生物品保存保持熔断，无重复爆炸或免费复原");
        var appearance = BoardAppearance.read(wand);
        h.assertTrue(
                appearance != null
                        && appearance.remaining() == 0
                        && appearance.melted()
                        && appearance.capacity() == 128 * F,
                "损坏模型只需小型原生观察数据");
        wand.getItem().hurtEnemy(wand, p, p);
        h.assertTrue(
                wand.getCount() == 1 && wand.getDamageValue() == 1 && a.board(1).melted(),
                "熔断后仍可近战，只磨损杖体");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void repairRequiresTableAndFreshPlate(GameTestHelper h) {
        var p = player(h);
        var wand = wand(p, 512 * F);
        var a = CarrierRepository.load(wand);
        a.circuit(1).start();
        advance(h, p, wand);
        p.getInventory().setItem(1, new ItemStack(ElementalCircuits.WOOD_PLATE.get(), 2));
        CarrierCommands.request(p, a.identity(), 1, 20, "");
        h.assertTrue(
                a.boardBroken(1) && p.getInventory().getItem(1).getCount() == 2, "空地不能用修复命令绕过工作台");
        var table = h.absolutePos(new BlockPos(3, 2, 2));
        h.getLevel().setBlockAndUpdate(table, ElementalCircuits.TABLE.get().defaultBlockState());
        InscriptionAccess.interact(p, table);
        var definition = a.circuit(1).definition();
        CarrierCommands.request(p, a.identity(), 1, 20, "");
        h.assertTrue(
                !a.boardBroken(1)
                        && a.board(1).spent() == 0
                        && p.getInventory().getItem(1).getCount() == 1,
                "工作台修复消耗实际同材质基板");
        h.assertTrue(
                a.circuit(1).definition().equals(definition) && wand.getDamageValue() == 0,
                "修复只换基板，保留原蓝图与杖体耐久");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void independentWearAndCreativeExemption(GameTestHelper h) {
        var p = player(h);
        var wand = wand(p, 4 * F);
        var a = CarrierRepository.load(wand);
        a.circuit(1).start();
        advance(h, p, wand);
        h.assertTrue(
                a.board(1).spent() > 0
                        && wand.getDamageValue() == 0
                        && ItemData.read(wand).getCompound("CastWear").getLong("Fraction") > 0,
                "施法主耗基板，小量杖体磨损保留小数");
        long boardWear = a.board(1).spent();
        wand.getItem().hurtEnemy(wand, p, p);
        h.assertTrue(wand.getDamageValue() == 1 && a.board(1).spent() == boardWear, "近战不磨损基板");
        p.getAbilities().instabuild = true;
        var creative = wand(p, 1024 * F);
        var ca = CarrierRepository.load(creative);
        ca.circuit(1).start();
        advance(h, p, creative);
        h.assertTrue(
                !ca.boardBroken(1) && ca.board(1).spent() == 0 && creative.getDamageValue() == 0,
                "创造模式两类耐久与熔毁均豁免，魔力仍按量支取");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void blastHasFalloffAndVanillaInvulnerability(GameTestHelper h) {
        var near = h.spawn(EntityType.COW, new BlockPos(2, 2, 2));
        var far = h.spawn(EntityType.COW, new BlockPos(6, 2, 2));
        near.setNoAi(true);
        far.setNoAi(true);
        var origin = near.position().add(0, .7, 0);
        BoardMeltdown.explode(h.getLevel(), null, origin, new BoardSafety.Blast(2, 6, 1));
        float health = near.getHealth();
        h.assertTrue(
                health < near.getMaxHealth() && far.getHealth() == far.getMaxHealth(),
                "公式半径内衰减伤害，范围外无伤害");
        BoardMeltdown.explode(h.getLevel(), null, origin, new BoardSafety.Blast(2, 6, 1));
        h.assertTrue(near.getHealth() == health && near.invulnerableTime > 0, "熔毁尊重原版受击间隔");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void offhandBookDoesNotStopWandCircuits(GameTestHelper h) {
        var p = player(h);
        var wand = wand(p, 4 * F);
        var a = CarrierRepository.load(wand);
        p.setItemInHand(
                net.minecraft.world.InteractionHand.OFF_HAND,
                new ItemStack(ElementalCircuits.SPELL_BOOK.get()));
        a.circuit(1).start();
        wand.getItem().inventoryTick(wand, h.getLevel(), p, 0, true);
        h.assertTrue(a.circuit(1).powered(), "副手书仅扩充轮盘，不自动停止法杖本身的法阵");
        advance(h, p, wand);
        h.assertTrue(
                a.circuit(1).snapshot().delivered() > 0 && a.board(1).spent() > 0,
                "书与杖共持时法杖施法及基板磨损照常工作");
        h.succeed();
    }
}
