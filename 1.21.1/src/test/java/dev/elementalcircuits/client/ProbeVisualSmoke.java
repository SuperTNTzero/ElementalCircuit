package dev.elementalcircuits.client;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.application.observation.SurveyReport;
import dev.elementalcircuits.block.*;
import dev.elementalcircuits.client.screen.ProbeScreen;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.network.ProbeNetwork;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.probe.ProbeService;

import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import org.lwjgl.glfw.GLFW;

import java.util.*;

@EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class ProbeVisualSmoke {
    private static int stage = -1, ticks, completedAt;
    private static volatile boolean ready;
    private static volatile int command, ack;
    private static volatile String fieldBefore;
    private static volatile long scanStarted;
    private static boolean hiddenCheck;
    private static SurveyReport lastReport;

    private static boolean enabled() {
        return Boolean.getBoolean("elementalcircuits.probeSmoke");
    }

    private static void capture(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), m -> {});
    }

    private static void next() {
        stage++;
        ticks = 0;
    }

    private static void onBlock(Minecraft mc, BlockPos pos) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
    }

    @SubscribeEvent
    public static void world(LevelTickEvent.Post e) {
        if (!enabled()
                || !(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || level.dimension() != Level.OVERWORLD
                || level.players().isEmpty()) return;
        var p = level.players().get(0);
        if (!ready) {
            p.getInventory().selected = 0;
            p.getInventory().setItem(0, new ItemStack(ElementalCircuits.PROBE_WAND.get()));
            var wand = new ItemStack(ElementalCircuits.WAND.get());
            var a = CarrierRepository.load(wand);
            a.replace(1, RecursionContracts.child(Element.FIRE, false));
            a.add(a.circuit(1).definition().rules());
            a.replace(2, RecursionContracts.child(Element.WATER, false));
            a.select(1);
            CarrierRepository.commit(wand);
            p.setItemSlot(EquipmentSlot.OFFHAND, wand);
            for (int x = -5; x <= 5; x++)
                for (int z = -2; z <= 8; z++) {
                    level.setBlock(
                            new BlockPos(x, -61, z),
                            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
                            3);
                    for (int y = -60; y <= -55; y++)
                        level.setBlock(
                                new BlockPos(x, y, z),
                                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                3);
                }
            var nodePos = new BlockPos(-2, -60, 3);
            level.setBlock(nodePos, ElementalCircuits.MANA_NODE.get().defaultBlockState(), 3);
            ((ManaNodeBlockEntity) level.getBlockEntity(nodePos)).insert();
            level.setBlock(
                    new BlockPos(1, -60, 4),
                    ElementalCircuits.NATURAL_MANA_NODE.get().defaultBlockState(),
                    3);
            var fixedPos = new BlockPos(2, -60, 3);
            level.setBlock(fixedPos, ElementalCircuits.CIRCUIT_BLOCK.get().defaultBlockState(), 3);
            var fixed = (CircuitBlockEntity) level.getBlockEntity(fixedPos);
            fixed.placed(new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get()), p);
            fixed.assembly().replace(1, RecursionContracts.child(Element.LIGHT, false));
            p.connection.teleport(.5, -60, .5, 0, 0);
            level.setDayTime(6000);
            ready = true;
        }
        if (command != ack) {
            switch (command) {
                case 1 -> {
                    p.getCooldowns().removeCooldown(ElementalCircuits.PROBE_WAND.get());
                    p.setShiftKeyDown(true);
                    ProbeService.use(p, InteractionHand.MAIN_HAND, null);
                    p.setShiftKeyDown(false);
                }
                case 2 -> {
                    p.getCooldowns().removeCooldown(ElementalCircuits.PROBE_WAND.get());
                    scanStarted = System.nanoTime();
                }
                default -> throw new AssertionError();
            }
            ack = command;
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (!enabled() || stage == 11) return;
        var mc = Minecraft.getInstance();
        mc.getToasts().clear();
        if (stage < 0) {
            if (mc.screen instanceof AccessibilityOnboardingScreen) {
                mc.options.onboardAccessibility = false;
                mc.setScreen(new TitleScreen());
            }
            if (++ticks > 1000) throw new AssertionError("探测验证标题页超时");
            if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
            mc.options.pauseOnLostFocus = false;
            mc.options.guiScale().set(2);
            mc.options.renderDistance().set(4);
            mc.options.simulationDistance().set(5);
            GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 800);
            mc.resizeDisplay();
            mc.getLanguageManager().setSelected("zh_cn");
            mc.options.languageCode = "zh_cn";
            mc.reloadResourcePacks();
            stage = 0;
            ticks = 0;
            return;
        }
        if (mc.getOverlay() != null) return;
        if (++ticks > 2500) throw new AssertionError("探测验证超时 stage=" + stage);
        switch (stage) {
            case 0 -> {
                if (ticks < 25) return;
                var settings =
                        new LevelSettings(
                                "探测杖隔离验证",
                                GameType.CREATIVE,
                                false,
                                Difficulty.PEACEFUL,
                                true,
                                new GameRules(),
                                WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows()
                        .createFreshLevel(
                                "probe-" + System.currentTimeMillis(),
                                settings,
                                new WorldOptions(20261101, false, false),
                                a ->
                                        a.registryOrThrow(Registries.WORLD_PRESET)
                                                .getHolderOrThrow(WorldPresets.FLAT)
                                                .value()
                                                .createWorldDimensions(),
                                new TitleScreen());
                next();
            }
            case 1 -> {
                if (!ready
                        || mc.player == null
                        || mc.screen != null
                        || mc.player.tickCount < 50
                        || !mc.player.getMainHandItem().is(ElementalCircuits.PROBE_WAND.get()))
                    return;
                var model =
                        mc.getItemRenderer()
                                .getModel(mc.player.getMainHandItem(), mc.level, mc.player, 0);
                if (model == mc.getModelManager().getMissingModel()
                        || model.getQuads(null, null, net.minecraft.util.RandomSource.create(0))
                                        .size()
                                < 60) throw new AssertionError("探测杖原生三维模型缺失");
                capture(mc, "probe-hand-world");
                onBlock(mc, new BlockPos(-2, -60, 3));
                next();
            }
            case 2 -> {
                if (!(mc.screen instanceof ProbeScreen s)
                        || !s.report().title().contains("人工")
                        || ticks < 15) return;
                capture(mc, "probe-node-report");
                mc.setScreen(null);
                next();
            }
            case 3 -> {
                if (ticks < 15) return;
                onBlock(mc, new BlockPos(2, -60, 3));
                next();
            }
            case 4 -> {
                if (!(mc.screen instanceof ProbeScreen s)
                        || !s.report().title().equals("固定法阵")
                        || ticks < 15) return;
                capture(mc, "probe-circuit-report");
                mc.setScreen(null);
                command = 1;
                next();
            }
            case 5 -> {
                if (!(mc.screen instanceof ProbeScreen s)
                        || ack != 1
                        || s.report().rows().stream().noneMatch(r -> r.name().contains("槽位 02"))
                        || ticks < 15) return;
                capture(mc, "probe-carried-two-slots");
                mc.setScreen(null);
                command = 2;
                next();
            }
            case 6 -> {
                if (ack != 2 || ticks < 15) return;
                mc.player.setXRot(-65);
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                next();
            }
            case 7 -> {
                if (!(mc.screen instanceof ProbeScreen s)
                        || !s.report().title().contains("勘测")
                        || ticks < 15) return;
                if (!s.report().done() && !hiddenCheck) {
                    capture(mc, "probe-survey-progress");
                    hiddenCheck = true;
                }
                if (!s.report().done()) return;
                if (s.report().rows().stream()
                        .noneMatch(r -> r.name().equals("天然节点") && !r.value().equals("0 个")))
                    throw new AssertionError("真实扫描没有找到已放置的天然节点");
                if (completedAt == 0) completedAt = ticks;
                if (ticks - completedAt < 8) return;
                capture(mc, "probe-survey-500-result");
                org.slf4j.LoggerFactory.getLogger("ProbeVisualSmoke")
                        .info(
                                "PROBE_SURVEY_TIME_MS: {}",
                                (System.nanoTime() - scanStarted) / 1000000);
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 960, 720);
                mc.options.guiScale().set(3);
                mc.resizeDisplay();
                next();
            }
            case 8 -> {
                if (ticks < 15) return;
                capture(mc, "probe-survey-small");
                mc.screen.mouseScrolled(100, 100, 0, -3);
                next();
            }
            case 9 -> {
                if (ticks < 15) return;
                capture(mc, "probe-survey-small-scrolled");
                lastReport = ((ProbeScreen) mc.screen).report();
                mc.setScreen(null);
                next();
            }
            case 10 -> {
                if (ticks < 20) return;
                ProbeScreen.accept(
                        new ProbeNetwork.Report(
                                UUID.randomUUID(),
                                mc.level.dimension().location(),
                                JsonCodecs.survey(lastReport),
                                false));
                ProbeScreen.accept(
                        new ProbeNetwork.Report(
                                UUID.randomUUID(),
                                net.minecraft.resources.ResourceLocation.withDefaultNamespace(
                                        "the_nether"),
                                JsonCodecs.survey(lastReport),
                                true));
                if (mc.screen != null) throw new AssertionError("迟到或跨维度报告重开页面");
                org.slf4j.LoggerFactory.getLogger("ProbeVisualSmoke")
                        .info(
                                "PROBE_GPU_PASS: 12-part static model; native node and circuit"
                                    + " interactions; other-hand carrier; real default500 sphere;"
                                    + " async progress and result; GUI2/3 scroll; dismissed reports"
                                    + " remain closed");
                mc.stop();
                stage = 11;
            }
        }
    }
}
