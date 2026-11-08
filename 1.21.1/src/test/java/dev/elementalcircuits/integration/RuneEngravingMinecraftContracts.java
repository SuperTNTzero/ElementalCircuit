package dev.elementalcircuits.integration;

import com.mojang.authlib.GameProfile;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.server.workshop.*;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

import java.util.UUID;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class RuneEngravingMinecraftContracts {
    @GameTest(template = "empty")
    public static void materialsAndAuthority(GameTestHelper h) {
        var player =
                FakePlayerFactory.get(
                        h.getLevel(), new GameProfile(UUID.randomUUID(), "RuneCraft"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        var pos = h.absolutePos(new BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(pos, RuneEngraving.TABLE.get().defaultBlockState());
        player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        var menu =
                new RuneEngravingMenu(
                        1, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), pos));
        player.getInventory().add(new ItemStack(Items.PAPER, 2));
        h.assertTrue(!menu.clickMenuButton(player, 0), "Missing dust rejects crafting");
        h.assertTrue(
                MaterialInventory.count(player, Items.PAPER) == 2, "Rejection preserves paper");
        player.getInventory().add(new ItemStack(RuneEngraving.DUST.get(), 2));
        h.assertTrue(
                !menu.clickMenuButton(player, -1)
                        && !menu.clickMenuButton(player, RuneEngraving.KEYS.size()),
                "Reject forged output IDs");
        h.assertTrue(menu.clickMenuButton(player, 0), "Craft at the workstation");
        var result = RuneEngraving.RUNES.get(RuneEngraving.KEYS.getFirst()).get();
        h.assertTrue(
                MaterialInventory.count(player, result) == 1
                        && MaterialInventory.count(player, Items.PAPER) == 1
                        && MaterialInventory.count(player, RuneEngraving.DUST.get()) == 1,
                "Consumes exactly one paper and dust for one rune");
        h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        h.assertTrue(
                !menu.clickMenuButton(player, 0), "Removed workstation rejects stale requests");
        h.assertTrue(
                MaterialInventory.count(player, RuneEngraving.DUST.get()) == 1,
                "Stale request preserves inventory");
        h.succeed();
    }
}
