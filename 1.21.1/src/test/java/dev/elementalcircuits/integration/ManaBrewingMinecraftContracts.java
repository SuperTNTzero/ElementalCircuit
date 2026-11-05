package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.mana.ManaBrewing;
import dev.elementalcircuits.server.mana.PlayerMana;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.neoforged.neoforge.gametest.*;

import java.util.UUID;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class ManaBrewingMinecraftContracts {
    private static ServerPlayer player(GameTestHelper h) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "mana-potion-test"));
    }

    private static boolean potion(ItemStack stack, net.minecraft.core.Holder<Potion> p) {
        return stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(p);
    }

    private static long percent(net.minecraft.server.level.ServerPlayer p) {
        return PlayerMana.get(p).capacity()
                * ServerSettings.mana("player.percentRegeneration")
                / Fixed.ONE;
    }

    @GameTest(template = "empty")
    public static void nativeRecipesAndBottleForms(GameTestHelper h) {
        var b = h.getLevel().potionBrewing();
        var awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        var mana = b.mix(new ItemStack(ElementalCircuits.MANA_CRYSTAL.get()), awkward);
        h.assertTrue(potion(mana, ManaBrewing.MANA), "粗制药水加水晶酿造魔力药水");
        h.assertTrue(
                potion(b.mix(new ItemStack(Items.REDSTONE), mana), ManaBrewing.LONG_MANA), "红石延时");
        h.assertTrue(
                potion(b.mix(new ItemStack(Items.GLOWSTONE_DUST), mana), ManaBrewing.STRONG_MANA),
                "萤石增强");
        var splash = b.mix(new ItemStack(Items.GUNPOWDER), mana);
        h.assertTrue(
                splash.is(Items.SPLASH_POTION) && potion(splash, ManaBrewing.MANA), "火药保留自定义效果");
        var lingering = b.mix(new ItemStack(Items.DRAGON_BREATH), splash);
        h.assertTrue(
                lingering.is(Items.LINGERING_POTION) && potion(lingering, ManaBrewing.MANA),
                "龙息保留效果");
        var water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        h.assertTrue(
                !potion(
                        b.mix(new ItemStack(ElementalCircuits.MANA_CRYSTAL.get()), water),
                        ManaBrewing.MANA),
                "不能跳过粗制药水");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void brewingStandConsumesRealFuelAndCrystal(GameTestHelper h) {
        var pos = h.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(pos, Blocks.BREWING_STAND.defaultBlockState());
        var stand = (BrewingStandBlockEntity) h.getLevel().getBlockEntity(pos);
        for (int i = 0; i < 3; i++)
            stand.setItem(i, PotionContents.createItemStack(Items.POTION, Potions.AWKWARD));
        stand.setItem(3, new ItemStack(ElementalCircuits.MANA_CRYSTAL.get(), 2));
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        for (int i = 0; i < 401; i++)
            BrewingStandBlockEntity.serverTick(
                    h.getLevel(), pos, h.getLevel().getBlockState(pos), stand);
        for (int i = 0; i < 3; i++)
            h.assertTrue(potion(stand.getItem(i), ManaBrewing.MANA), "三瓶真实酿造");
        h.assertTrue(
                stand.getItem(3).getCount() == 1 && stand.getItem(4).isEmpty(), "只消费一颗水晶和一份燃料");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void drinkingMilkAndNoCompounding(GameTestHelper h) {
        var p = player(h);
        p.getAbilities().instabuild = false;
        PlayerMana.recovery(p, 4 * Fixed.ONE, 40, false);
        var pool = PlayerMana.get(p);
        pool.debit(32 * Fixed.ONE);
        var before = pool.snapshot();
        var bottle = PotionContents.createItemStack(Items.POTION, ManaBrewing.MANA);
        var glass = bottle.getItem().finishUsingItem(bottle, h.getLevel(), p);
        h.assertTrue(
                glass.is(Items.GLASS_BOTTLE) && p.hasEffect(ManaBrewing.RECOVERY),
                "真实饮用返还玻璃瓶并产生效果");
        for (int i = 0; i < 50; i++)
            h.assertTrue(PlayerMana.get(p).regeneration() == 8 * Fixed.ONE + percent(p), "反复读取不叠乘");
        h.assertTrue(pool.snapshot().equals(before), "饮用不补满、不清等待");
        for (int i = 0; i < 40; i++) PlayerMana.get(p).tick();
        h.assertTrue(pool.available() == 32 * Fixed.ONE, "等待40tick");
        for (int i = 0; i < 20; i++) PlayerMana.get(p).tick();
        h.assertTrue(pool.available() == 40 * Fixed.ONE + percent(p), "效果I实际每秒恢复8");
        var milk = new ItemStack(Items.MILK_BUCKET);
        milk.getItem().finishUsingItem(milk, h.getLevel(), p);
        h.assertTrue(
                !p.hasEffect(ManaBrewing.RECOVERY)
                        && PlayerMana.get(p).regeneration() == 4 * Fixed.ONE + percent(p),
                "牛奶清除效果并回到管理员基础值");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void effectsPersistAndExpireWithoutChangingBase(GameTestHelper h) {
        var p = player(h);
        PlayerMana.recovery(p, 3 * Fixed.ONE, 0, false);
        var pool = PlayerMana.get(p);
        pool.debit(40 * Fixed.ONE);
        p.addEffect(new MobEffectInstance(ManaBrewing.RECOVERY, 2, 1));
        h.assertTrue(
                PlayerMana.get(p).regeneration() == 9 * Fixed.ONE + percent(p), "II级按玩家独立基础值计算");
        var state = pool.snapshot();
        PlayerMana.save(p);
        PlayerMana.forget(p);
        h.assertTrue(
                PlayerMana.get(p).snapshot().equals(state)
                        && PlayerMana.get(p).regeneration() == 9 * Fixed.ONE + percent(p),
                "重新绑定槽不重复放大或补能");
        var encoded = p.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        var restored = player(h);
        restored.load(encoded);
        h.assertTrue(
                restored.hasEffect(ManaBrewing.RECOVERY)
                        && restored.getEffect(ManaBrewing.RECOVERY).getAmplifier() == 1,
                "原版NBT保存药效和等级");
        var effect = p.getEffect(ManaBrewing.RECOVERY);
        h.assertTrue(effect.tick(p, () -> {}) && !effect.tick(p, () -> {}), "原版持续时间耗尽");
        p.removeEffect(ManaBrewing.RECOVERY);
        h.assertTrue(PlayerMana.get(p).regeneration() == 3 * Fixed.ONE + percent(p), "药效结束保留基础配置");
        PlayerMana.recovery(p, 0L, null, false);
        p.addEffect(new MobEffectInstance(ManaBrewing.RECOVERY, 100, 1));
        h.assertTrue(PlayerMana.get(p).regeneration() == percent(p), "固定回复为零时药水不放大容量回复分支");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void durationsAndNativeStrongerEffectRules(GameTestHelper h) {
        h.assertTrue(
                ManaBrewing.MANA.value().getEffects().getFirst().getDuration() == 3600, "普通3分钟");
        h.assertTrue(
                ManaBrewing.LONG_MANA.value().getEffects().getFirst().getDuration() == 9600,
                "延时8分钟");
        h.assertTrue(
                ManaBrewing.STRONG_MANA.value().getEffects().getFirst().getDuration() == 1800,
                "增强90秒");
        var p = player(h);
        p.addEffect(new MobEffectInstance(ManaBrewing.RECOVERY, 1800, 1));
        p.addEffect(new MobEffectInstance(ManaBrewing.RECOVERY, 3600, 0));
        h.assertTrue(p.getEffect(ManaBrewing.RECOVERY).getAmplifier() == 1, "低等级不能覆盖当前强效");
        h.succeed();
    }
}
