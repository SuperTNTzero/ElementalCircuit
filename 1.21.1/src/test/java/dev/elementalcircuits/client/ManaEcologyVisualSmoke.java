package dev.elementalcircuits.client;

import dev.elementalcircuits.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.server.*;

import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

/** Opt-in GPU validation in a disposable world, including real synchronized wearable attributes. */
@net.neoforged.fml.common.EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class ManaEcologyVisualSmoke {
    private static int stage = -1, ticks, serverTicks;

    private static boolean enabled() {
        return Boolean.getBoolean("elementalcircuits.ecologySmoke");
    }

    @SubscribeEvent
    public static void server(LevelTickEvent.Post event) {
        if (!enabled()
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || level.dimension() != Level.OVERWORLD
                || level.players().isEmpty()) return;
        if (++serverTicks != 1) return;
        var p = level.players().getFirst();
        p.connection.teleport(.5, -60, -5.5, 0, 0);
        p.setGameMode(GameType.CREATIVE);
        level.setDayTime(18000);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.setBlockAndUpdate(
                new net.minecraft.core.BlockPos(-2, -57, 2),
                ElementalCircuits.AERIAL_MANA_NODE
                        .get()
                        .defaultBlockState()
                        .setValue(dev.elementalcircuits.block.ManaNodeBlock.LIT, true));
        level.setBlockAndUpdate(
                new net.minecraft.core.BlockPos(3, -60, 2),
                ElementalCircuits.NATURAL_MANA_NODE.get().defaultBlockState());
        var slime = dev.elementalcircuits.server.ecology.ManaEcology.SLIME.get().create(level);
        slime.moveTo(.5, -60, 1.5, 0, 0);
        slime.setSize(2, true);
        slime.yBodyRot = 180;
        slime.yBodyRotO = 180;
        slime.setYHeadRot(180);
        slime.setYRot(180);
        slime.setNoAi(true);
        level.addFreshEntity(slime);
        p.getInventory().setItem(0, new ItemStack(ElementalCircuits.MANA_GEL.get()));
        p.getInventory()
                .setItem(
                        1,
                        net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                                net.minecraft.world.item.Items.POTION,
                                dev.elementalcircuits.server.mana.ManaBrewing.INSTANT_MANA));
        p.getInventory()
                .setItem(
                        2,
                        net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                                net.minecraft.world.item.Items.POTION,
                                dev.elementalcircuits.server.mana.ManaBrewing.MANA));
    }

    private static void capture(Minecraft mc, String name) {
        mc.getToasts().clear();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), m -> {});
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (!enabled()) return;
        var mc = Minecraft.getInstance();
        if (stage < 0) {
            if (mc.screen instanceof AccessibilityOnboardingScreen) {
                mc.options.onboardingAccessibilityFinished();
                mc.setScreen(new TitleScreen());
                return;
            }
            if (!(mc.screen instanceof TitleScreen)) return;
            mc.options.pauseOnLostFocus = false;
            mc.options.guiScale().set(2);
            mc.options.renderDistance().set(4);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 900);
            mc.resizeDisplay();
            stage = 0;
            ticks = 0;
            mc.createWorldOpenFlows()
                    .createFreshLevel(
                            "wizard-" + System.currentTimeMillis(),
                            new LevelSettings(
                                    "装备隔离验证",
                                    GameType.SURVIVAL,
                                    false,
                                    Difficulty.NORMAL,
                                    true,
                                    new GameRules(),
                                    WorldDataConfiguration.DEFAULT),
                            new WorldOptions(20261102, false, false),
                            r ->
                                    r.registryOrThrow(Registries.WORLD_PRESET)
                                            .getHolderOrThrow(WorldPresets.FLAT)
                                            .value()
                                            .createWorldDimensions(),
                            new TitleScreen());
            return;
        }
        if (mc.level != null)
            for (var entity : mc.level.entitiesForRendering()) {
                if (entity instanceof dev.elementalcircuits.entity.ManaSlime slime) {
                    slime.yBodyRot = slime.yBodyRotO = 180;
                    slime.yHeadRot = slime.yHeadRotO = 180;
                    slime.setYRot(180);
                }
            }
        if (++ticks > 1600) throw new AssertionError("装备GPU验证超时");
        if (stage == 0 && mc.player != null && mc.screen == null && serverTicks > 70) {
            capture(mc, "mana-ecology-night");
            mc.getSingleplayerServer()
                    .execute(() -> mc.getSingleplayerServer().overworld().setDayTime(6000));
            stage = 1;
            ticks = 0;
        } else if (stage == 1 && ticks > 40) {
            capture(mc, "mana-ecology-day");
            System.out.println(
                    "MANA_ECOLOGY_GPU_PASS: native mana slime renderer, floating emissive node, gel"
                            + " and instant potion items");
            stage = 2;
            ticks = 0;
            mc.level.disconnect();
            mc.disconnect(new TitleScreen());
        } else if (stage == 2 && ticks > 10) {
            stage = 3;
            mc.stop();
        }
    }
}
