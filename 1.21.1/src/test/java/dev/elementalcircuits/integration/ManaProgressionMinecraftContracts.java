package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.casting.WandCooldown;
import dev.elementalcircuits.server.circuit.WorldCircuits;
import dev.elementalcircuits.server.mana.PlayerMana;

import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class ManaProgressionMinecraftContracts {
    private static ServerPlayer player(GameTestHelper h) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "mana-fruit-test"));
    }

    @GameTest(template = "empty")
    public static void fruitRecipeAndConsumption(GameTestHelper h) {
        var ingredients = new ArrayList<ItemStack>();
        ingredients.add(new ItemStack(Items.APPLE));
        for (int i = 0; i < 4; i++)
            ingredients.add(new ItemStack(ElementalCircuits.MANA_CRYSTAL.get()));
        for (int i = 0; i < 4; i++) ingredients.add(ItemStack.EMPTY);
        var input = CraftingInput.of(3, 3, ingredients);
        var recipe =
                (CraftingRecipe)
                        h.getLevel()
                                .getRecipeManager()
                                .byKey(
                                        ResourceLocation.fromNamespaceAndPath(
                                                ElementalCircuits.ID, "mana_fruit"))
                                .orElseThrow()
                                .value();
        h.assertTrue(
                recipe.matches(input, h.getLevel())
                        && recipe.assemble(input, h.getLevel().registryAccess())
                                .is(ElementalCircuits.MANA_FRUIT.get()),
                "苹果和四颗法力水晶可真实合成魔力果实");
        ingredients.set(4, ItemStack.EMPTY);
        h.assertTrue(!recipe.matches(CraftingInput.of(3, 3, ingredients), h.getLevel()), "缺水晶不可合成");
        var p = player(h);
        p.getAbilities().instabuild = false;
        p.getFoodData().setFoodLevel(20);
        var pool = PlayerMana.get(p);
        pool.debit(10 * Fixed.ONE);
        var before = pool.snapshot();
        var fruit = new ItemStack(ElementalCircuits.MANA_FRUIT.get(), 3);
        p.setItemInHand(InteractionHand.MAIN_HAND, fruit);
        h.assertTrue(
                fruit.getItem()
                        .use(h.getLevel(), p, InteractionHand.MAIN_HAND)
                        .getResult()
                        .consumesAction(),
                "满饥饿仍可开始食用");
        h.assertTrue(pool.capacity() == 64 * Fixed.ONE && fruit.getCount() == 3, "开始食用不提前升级或消耗");
        p.stopUsingItem();
        fruit.getItem().finishUsingItem(fruit, h.getLevel(), p);
        h.assertTrue(
                fruit.getCount() == 2
                        && pool.capacity() == 80 * Fixed.ONE
                        && pool.available() == before.amount(),
                "食用完成才消耗一颗并增容，不补能");
        h.assertTrue(
                pool.snapshot().waitTicks() == before.waitTicks()
                        && pool.snapshot().spent() == before.spent(),
                "成长不清除原有消费和恢复等待");
        var saved = pool.snapshot();
        PlayerMana.forget(p);
        h.assertTrue(PlayerMana.get(p).snapshot().equals(saved), "重新加载玩家数据保留容量与完整现场");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void growthSurvivesDeathAndStopsAtCap(GameTestHelper h) {
        var p = player(h);
        p.getAbilities().instabuild = false;
        var pool = PlayerMana.get(p);
        pool.debit(5 * Fixed.ONE);
        var fruit = new ItemStack(ElementalCircuits.MANA_FRUIT.get(), 64);
        for (int i = 0; i < 60; i++) fruit.getItem().finishUsingItem(fruit, h.getLevel(), p);
        h.assertTrue(
                fruit.getCount() == 4
                        && pool.capacity() == 1024 * Fixed.ONE
                        && pool.available() == 59 * Fixed.ONE,
                "真实消费60颗从64到1024，不凭空补能");
        var before = pool.snapshot();
        fruit.getItem().finishUsingItem(fruit, h.getLevel(), p);
        h.assertTrue(fruit.getCount() == 4 && pool.snapshot().equals(before), "到顶后不消耗果实，也不重复写入成长");
        p.setItemInHand(InteractionHand.MAIN_HAND, fruit);
        h.assertTrue(
                !fruit.getItem()
                        .use(h.getLevel(), p, InteractionHand.MAIN_HAND)
                        .getResult()
                        .consumesAction(),
                "上限拒绝开始食用");
        var respawn = player(h);
        PlayerMana.clone(p, respawn);
        h.assertTrue(PlayerMana.get(respawn).snapshot().equals(before), "死亡克隆保留永久上限、当前量和恢复等待");
        h.succeed();
    }

    private static void cast(GameTestHelper h, ServerPlayer p, ItemStack carrier) {
        var a = CarrierRepository.load(carrier);
        var b =
                AssemblyContracts.line(
                                false,
                                Route.Payload.MANA,
                                Rune.Kind.INPUT,
                                Rune.Kind.CONVERT,
                                Rune.Kind.OUTPUT)
                        .withInputAmount(1, 4 * Fixed.ONE);
        a.replace(1, b);
        a.circuit(1).start();
        for (int i = 0; i < 7; i++) {
            WorldCircuits.enqueue(carrier, p);
            WorldCircuits.tick(h.getLevel());
        }
        h.assertTrue(a.circuit(1).snapshot().delivered() > 0, "实际VM交付元素法术");
    }

    @GameTest(template = "empty")
    public static void creativeCastingAndMeleeDoNotWear(GameTestHelper h) {
        var p = player(h);
        p.getAbilities().instabuild = true;
        var wand = new ItemStack(ElementalCircuits.WAND.get());
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, wand);
        wand.setDamageValue(17);
        cast(h, p, wand);
        h.assertTrue(
                wand.getDamageValue() == 17 && !WandCooldown.state(wand).ready(),
                "创造施法不磨损，释放CD正常生效");
        wand.getItem().hurtEnemy(wand, p, p);
        h.assertTrue(wand.getDamageValue() == 17, "创造近战不磨损");
        for (int i = 0; i < 20; i++) WandCooldown.tick(wand);
        var book = new ItemStack(ElementalCircuits.SPELL_BOOK.get());
        p.setItemSlot(EquipmentSlot.OFFHAND, book);
        cast(h, p, book);
        h.assertTrue(wand.getDamageValue() == 17, "副手法术书施法也不磨损创造法杖");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void survivalWearAndConfiguredPaymentRemain(GameTestHelper h) {
        var p = player(h);
        p.getAbilities().instabuild = false;
        var wand = new ItemStack(ElementalCircuits.WAND.get());
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, wand);
        cast(h, p, wand);
        h.assertTrue(
                wand.getDamageValue() == 0
                        && CarrierRepository.load(wand).board(1).spent() > 0
                        && ItemData.read(wand).getCompound("CastWear").getLong("Fraction") > 0
                        && PlayerMana.get(p).snapshot().spent() == 4 * Fixed.ONE,
                "生存施法磨损基板并累积少量杖体磨损，按配置输入量付费");
        wand.getItem().hurtEnemy(wand, p, p);
        h.assertTrue(wand.getDamageValue() == 1, "生存近战仍正常磨损");
        var restored =
                CarrierRepository.load(
                        ItemStack.parseOptional(
                                h.getLevel().registryAccess(),
                                (net.minecraft.nbt.CompoundTag)
                                        wand.save(
                                                h.getLevel().registryAccess(),
                                                new net.minecraft.nbt.CompoundTag())));
        h.assertTrue(
                restored.circuit(1).definition().version() == 5
                        && restored.circuit(1)
                                        .definition()
                                        .inputAmount(restored.circuit(1).definition().rune(1))
                                == 4 * Fixed.ONE,
                "物品保存恢复保留可调输入及现代现场");
        h.succeed();
    }
}
