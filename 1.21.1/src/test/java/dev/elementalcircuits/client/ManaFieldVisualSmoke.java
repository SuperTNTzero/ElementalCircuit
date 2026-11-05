package dev.elementalcircuits.client;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.adapter.resources.WandResources;
import dev.elementalcircuits.block.*;
import dev.elementalcircuits.client.render.*;
import dev.elementalcircuits.client.screen.CircuitScreen;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.examples.ManaFieldExamples;
import dev.elementalcircuits.network.CircuitNetwork;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.casting.WandCooldown;
import dev.elementalcircuits.server.mana.WorldManaField;

import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

/** Explicit opt-in GPU and integrated-server check in a fresh disposable world. */
@EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class ManaFieldVisualSmoke {
    private static int stage = -1, ticks, serverTicks;
    private static volatile boolean initialized, worldPassed;
    private static boolean emptySeen, halfSeen, readySeen;
    private static ManaNodeBlockEntity node;
    private static CircuitBlockEntity circuit;

    private static boolean enabled() {
        return Boolean.getBoolean("elementalcircuits.fieldSmoke");
    }

    private static void capture(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), m -> {});
    }

    private static List<ItemStack> items() {
        return List.of(
                new ItemStack(ElementalCircuits.MANA_CRYSTAL.get()),
                new ItemStack(ElementalCircuits.MANA_ORE_ITEM.get()),
                new ItemStack(ElementalCircuits.DEEPSLATE_MANA_ORE_ITEM.get()),
                new ItemStack(ElementalCircuits.NATURAL_MANA_NODE_ITEM.get()),
                new ItemStack(ElementalCircuits.MANA_NODE_ITEM.get()));
    }

    private static final class Gallery extends Screen {
        Gallery() {
            super(Component.literal("魔力水晶与节点"));
        }

        @Override
        public void render(GuiGraphics g, int x, int y, float dt) {
            WorkshopSkin.background(g, width, height);
            g.drawString(font, title, 24, 18, WorkshopSkin.TEXT, false);
            var stacks = items();
            for (int i = 0; i < stacks.size(); i++) {
                int cx = 50 + i * (width - 80) / 5;
                g.pose().pushPose();
                g.pose().translate(cx, 62, 0);
                g.pose().scale(2.5f, 2.5f, 1);
                g.renderItem(stacks.get(i), 0, 0);
                g.pose().popPose();
                g.drawString(
                        font, stacks.get(i).getHoverName(), cx - 10, 116, WorkshopSkin.TEXT, false);
            }
            GlyphAtlas.draw(g, "rune/ABSORB", 48, 160, 12, 0xff83d8f6);
            g.drawString(font, "环境汲取", 72, 156, WorkshopSkin.TEXT, false);
            g.drawString(font, "节点向周围供能，法阵从当地魔力场取能", 24, 186, WorkshopSkin.TEXT, false);
            var wand = new ItemStack(ElementalCircuits.WAND.get());
            g.renderItem(wand, 28, 222);
            g.renderTooltip(font, wand, 58, 222);
        }
    }

    @SubscribeEvent
    public static void world(LevelTickEvent.Post event) {
        if (!enabled()
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || level.dimension() != Level.OVERWORLD
                || level.players().isEmpty()) return;
        var p = level.players().get(0);
        if (!initialized) {
            p.getInventory().selected = 0;
            p.getInventory().setItem(0, new ItemStack(ElementalCircuits.WAND.get()));
            p.connection.teleport(.5, -59, -1.5, 0, 12);
            level.setDayTime(6000);
            for (int i = 0; i < 4; i++)
                level.setBlock(
                        new BlockPos(-3 + i * 2, -60, 4),
                        List.of(
                                        ElementalCircuits.MANA_ORE.get(),
                                        ElementalCircuits.DEEPSLATE_MANA_ORE.get(),
                                        ElementalCircuits.NATURAL_MANA_NODE.get(),
                                        ElementalCircuits.MANA_NODE.get())
                                .get(i)
                                .defaultBlockState(),
                        3);
            node = (ManaNodeBlockEntity) level.getBlockEntity(new BlockPos(3, -60, 4));
            node.insert();
            var pos = new BlockPos(3, -60, 7);
            level.setBlock(
                    pos,
                    ElementalCircuits.CIRCUIT_BLOCK
                            .get()
                            .defaultBlockState()
                            .setValue(CircuitBlock.FACING, Direction.WEST),
                    3);
            circuit = (CircuitBlockEntity) level.getBlockEntity(pos);
            circuit.placed(new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get()), p);
            circuit.assembly().replace(1, ManaFieldExamples.beam(RuleResources.defaults()));
            circuit.assembly().circuit(1).start();
            initialized = true;
        }
        serverTicks++;
        if (serverTicks == 30 || serverTicks == 90 || serverTicks == 150)
            WandCooldown.tryRelease(p.getMainHandItem());
        if (serverTicks == 180) {
            if (node.remaining() >= 64 * Fixed.ONE
                    || circuit.available() != 0
                    || circuit.assembly().circuit(1).snapshot().supplied() == 0)
                throw new AssertionError("真实服务器节点或环境汲取没有运行");
            var data = WorldManaField.get(level);
            if (data.field().snapshot().injected() == 0 || data.field().snapshot().extracted() == 0)
                throw new AssertionError("世界场账本未记录源与汇");
            worldPassed = true;
            org.slf4j.LoggerFactory.getLogger("ManaFieldVisualSmoke")
                    .info(
                            "MANA_FIELD_WORLD_PASS: real field diffusion, artificial fuel depletion"
                                    + " and zero-buffer fixed absorption");
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!enabled()) return;
        var mc = Minecraft.getInstance();
        if (stage < 0) {
            if (mc.screen instanceof AccessibilityOnboardingScreen) {
                mc.options.onboardAccessibility = false;
                mc.setScreen(new TitleScreen());
            }
            if (!(mc.screen instanceof TitleScreen)) return;
            mc.options.pauseOnLostFocus = false;
            mc.options.guiScale().set(2);
            mc.options.renderDistance().set(4);
            mc.options.simulationDistance().set(5);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 800);
            mc.resizeDisplay();
            mc.getLanguageManager().setSelected("zh_cn");
            mc.options.languageCode = "zh_cn";
            mc.reloadResourcePacks();
            for (var item : items())
                if (mc.getItemRenderer().getModel(item, null, null, 0)
                        == mc.getModelManager().getMissingModel())
                    throw new AssertionError("缺少实际物品模型");
            stage = 0;
            ticks = 0;
            mc.setScreen(new Gallery());
            return;
        }
        if (mc.getOverlay() != null) return;
        if (++ticks > 1200) throw new AssertionError("魔力场客户端验证超时");
        if (stage == 0 && ticks > 60) {
            capture(mc, "mana-field-items-tooltip");
            var board = ManaFieldExamples.beam(RuleResources.defaults());
            mc.setScreen(
                    new CircuitScreen(
                            new CircuitNetwork.Editor(
                                    UUID.randomUUID(),
                                    JsonCodecs.blueprint(board),
                                    false,
                                    0,
                                    false,
                                    "环境光束",
                                    WandResources.definitions().basic(),
                                    0)));
            stage = 1;
            ticks = 0;
            return;
        }
        if (stage == 1 && ticks > 35) {
            capture(mc, "mana-field-rune-editor");
            stage = 2;
            ticks = 0;
            var settings =
                    new LevelSettings(
                            "魔力场隔离验证",
                            GameType.CREATIVE,
                            false,
                            Difficulty.PEACEFUL,
                            true,
                            new GameRules(),
                            WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows()
                    .createFreshLevel(
                            "mana-field-" + System.currentTimeMillis(),
                            settings,
                            new WorldOptions(20261031, false, false),
                            a ->
                                    a.registryOrThrow(Registries.WORLD_PRESET)
                                            .getHolderOrThrow(WorldPresets.FLAT)
                                            .value()
                                            .createWorldDimensions(),
                            new TitleScreen());
            return;
        }
        if (stage != 2
                || mc.level == null
                || mc.player == null
                || mc.screen != null
                || !initialized) return;
        var wand = mc.player.getMainHandItem();
        if (!wand.is(ElementalCircuits.WAND.get())) return;
        var cd = WandCooldown.state(wand);
        if (cd.remaining() > 2 && !emptySeen) {
            capture(mc, "mana-field-cooldown-empty");
            emptySeen = true;
        }
        if (cd.duration() > 0
                && cd.remaining() >= cd.duration() / 2 - 1
                && cd.remaining() <= cd.duration() / 2 + 1
                && !halfSeen) {
            capture(mc, "mana-field-cooldown-half");
            halfSeen = true;
        }
        if (emptySeen && cd.ready() && !readySeen) {
            capture(mc, "mana-field-cooldown-ready");
            readySeen = true;
        }
        if (worldPassed && emptySeen && halfSeen && readySeen) {
            capture(mc, "mana-field-world");
            stage = 3;
            ticks = 0;
            mc.level.disconnect();
            mc.disconnect(new TitleScreen());
            org.slf4j.LoggerFactory.getLogger("ManaFieldVisualSmoke")
                    .info(
                            "MANA_FIELD_GPU_PASS: five native item models, localized concise"
                                + " tooltip, absorb glyph/editor and three transparent HUD states");
        }
    }

    @SubscribeEvent
    public static void finish(ClientTickEvent.Pre event) {
        if (enabled() && stage == 3 && ++ticks > 10) {
            stage = 4;
            Minecraft.getInstance().stop();
        }
    }
}
