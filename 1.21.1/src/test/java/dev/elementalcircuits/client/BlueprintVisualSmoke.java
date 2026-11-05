package dev.elementalcircuits.client;

import dev.elementalcircuits.*;
import dev.elementalcircuits.adapter.codec.BlueprintTextCode;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.client.input.BookClient;
import dev.elementalcircuits.client.screen.NotebookScreen;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.testing.TestBlueprints;

import net.minecraft.client.*;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

@net.neoforged.fml.common.EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class BlueprintVisualSmoke {
    private static int stage = -1, ticks;
    private static boolean injected;
    private static volatile boolean serverVerified;

    private static boolean enabled() {
        return Boolean.getBoolean("elementalcircuits.blueprintSmoke");
    }

    @SubscribeEvent
    public static void server(LevelTickEvent.Post e) {
        if (!enabled()
                || serverVerified
                || !(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || level.dimension() != Level.OVERWORLD
                || level.players().isEmpty()) return;
        var p = level.players().getFirst();
        if (!injected) {
            injected = true;
            p.setGameMode(GameType.CREATIVE);
            p.connection.teleport(.5, -60, .5, 0, 0);
            level.setDayTime(6000);
            p.getInventory().selected = 0;
            var book = new ItemStack(ElementalCircuits.NOTEBOOK.get());
            var volume = TestBlueprints.fill(NotebookRepository.load(book));
            NotebookRepository.save(book, volume);
            p.getInventory().setItem(1, book);
            var wand = new ItemStack(ElementalCircuits.WAND.get());
            CarrierRepository.load(wand).replace(1, volume.page(1).blueprint());
            CarrierRepository.commit(wand);
            p.setItemSlot(EquipmentSlot.MAINHAND, wand);
            p.getInventory().setItem(2, new ItemStack(ElementalCircuits.TABLE_ITEM.get()));
            level.setBlockAndUpdate(
                    new BlockPos(2, -60, 0), ElementalCircuits.TABLE.get().defaultBlockState());
            p.getInventory().setChanged();
        }
        var v = NotebookRepository.load(p.getInventory().getItem(1));
        int count = TestBlueprints.entries().size();
        if (v.selected() == count && v.pages().size() == count) serverVerified = true;
    }

    private static void click(Screen s, String text) {
        for (var c : List.copyOf(s.children()))
            if (c instanceof AbstractWidget w && w.getMessage().getString().equals(text)) {
                s.mouseClicked(w.getX() + 4, w.getY() + 4, 0);
                return;
            }
        throw new AssertionError("Missing notebook control: " + text);
    }

    @SubscribeEvent
    public static void client(ClientTickEvent.Post e) {
        if (!enabled()) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) {
            ticks = 0;
            return;
        }
        if (stage < 0) {
            if (mc.screen instanceof AccessibilityOnboardingScreen) {
                mc.options.onboardingAccessibilityFinished();
                mc.setScreen(new TitleScreen());
                return;
            }
            if (!(mc.screen instanceof TitleScreen)) return;
            mc.options.pauseOnLostFocus = false;
            mc.options.guiScale().set(2);
            mc.options.renderDistance().set(3);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1440, 900);
            mc.resizeDisplay();
            stage = 0;
            ticks = 0;
            mc.createWorldOpenFlows()
                    .createFreshLevel(
                            "blueprints-" + System.currentTimeMillis(),
                            new LevelSettings(
                                    "蓝图隔离试验",
                                    GameType.CREATIVE,
                                    false,
                                    Difficulty.PEACEFUL,
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
        if (stage == 5) return;
        if (++ticks > 1800) throw new AssertionError("Blueprint injection timeout");
        if (stage == 0 && mc.player != null && mc.screen == null && ticks > 30) {
            var book = mc.player.getInventory().getItem(1);
            if (!book.is(ElementalCircuits.NOTEBOOK.get())) return;
            var v = NotebookRepository.load(book);
            if (v.pages().size() != TestBlueprints.entries().size())
                throw new AssertionError("Notebook inventory did not synchronize");
            BookClient.openNotebook();
            stage = 1;
            ticks = 0;
        } else if (stage == 1 && mc.screen instanceof NotebookScreen && ticks > 20) {
            Screenshot.grab(
                    mc.gameDirectory, "blueprint-notebook.png", mc.getMainRenderTarget(), m -> {});
            for (int i = 0; i < 20; i++) mc.screen.mouseScrolled(20, 110, 0, -1);
            click(mc.screen, TestBlueprints.entries().getLast().title());
            stage = 2;
            ticks = 0;
        } else if (stage == 2
                && mc.screen instanceof NotebookScreen
                && ticks > 25
                && serverVerified) {
            click(mc.screen, "复制蓝图码");
            var expected = TestBlueprints.entries().getLast().blueprint();
            if (!BlueprintTextCode.decode(mc.keyboardHandler.getClipboard()).equals(expected))
                throw new AssertionError("Server-selected file blueprint clipboard mismatch");
            stage = 3;
            ticks = 0;
        } else if (stage == 3 && ticks > 12) {
            Screenshot.grab(
                    mc.gameDirectory,
                    "blueprint-contact-preview.png",
                    mc.getMainRenderTarget(),
                    m -> {});
            org.slf4j.LoggerFactory.getLogger("BlueprintSmoke")
                    .info(
                            "BLUEPRINT_NOTEBOOK_GPU_PASS: indexed file fixtures injected into real"
                                    + " player inventory; server page selection, native NBT sync,"
                                    + " preview and clipboard import verified");
            stage = 4;
            ticks = 0;
            if (Boolean.getBoolean("elementalcircuits.keepTestWorldOpen")) stage = 5;
        } else if (stage == 4 && ticks > 8) {
            stage = 5;
            mc.stop();
        }
    }
}
