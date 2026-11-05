package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;

import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder(ElementalCircuits.ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class GuideMinecraftContracts {
    @GameTest(template = "empty")
    public static void manualRecipeAndReadOnlyUse(GameTestHelper h) {
        var level = h.getLevel();
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var stack = new ItemStack(ElementalCircuits.GUIDE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var before = stack.save(level.registryAccess());
        var result = stack.getItem().use(level, player, InteractionHand.MAIN_HAND);
        h.assertTrue(
                result.getResult().consumesAction()
                        && ItemStack.isSameItemSameComponents(stack, result.getObject()),
                "指南正确响应原生使用");
        h.assertTrue(
                stack.save(level.registryAccess()).equals(before) && stack.getCount() == 1,
                "阅读不写入法阵、魔力或物品数据");
        var recipe =
                level.getRecipeManager()
                        .byKey(
                                ResourceLocation.fromNamespaceAndPath(
                                        ElementalCircuits.ID, "circuit_guide"))
                        .orElseThrow();
        h.assertTrue(
                recipe.value()
                        .getResultItem(level.registryAccess())
                        .is(ElementalCircuits.GUIDE.get()),
                "原生配方得到实际指南物品");
        h.succeed();
    }
}
