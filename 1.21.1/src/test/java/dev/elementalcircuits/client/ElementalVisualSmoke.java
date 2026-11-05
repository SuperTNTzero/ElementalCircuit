package dev.elementalcircuits.client;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.adapter.resources.ShapeResources;
import dev.elementalcircuits.application.observation.SpellVisual;
import dev.elementalcircuits.client.effect.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.circuit.MagicCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.server.spell.SpellEffects;

import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import org.joml.Quaternionf;

import java.util.*;

/** Opt-in isolated real world plus a material/shape GPU gallery. Never in the release JAR. */
@EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class ElementalVisualSmoke {
    private static final Element[] ELEMENTS = {
        Element.LIGHTNING,
        Element.FIRE,
        Element.WATER,
        Element.WIND,
        Element.EARTH,
        Element.LIGHT,
        Element.SHADOW,
        Element.ARCANE
    };
    private static final SpellSpec.Shape[] SHAPES = {
        SpellSpec.Shape.BEAM,
        SpellSpec.Shape.FAN,
        SpellSpec.Shape.FAN,
        SpellSpec.Shape.COLUMN,
        SpellSpec.Shape.WALL,
        SpellSpec.Shape.DOME,
        SpellSpec.Shape.RING,
        SpellSpec.Shape.SHIELD
    };
    private static final String[] NAMES = {
        "lightning-chain",
        "fire-fan",
        "water-fan",
        "wind-column",
        "earth-wall",
        "light-dome",
        "shadow-ring",
        "arcane-shield"
    };
    private static volatile int command = -1, ack = -2;
    private static volatile boolean initialized;
    private static int stage = -1, ticks, serverTicks, readyTicks;
    private static boolean requested;
    private static final List<Cow> targets = new ArrayList<>();
    private static final List<VisualTimeline.Frame> gallery = new ArrayList<>();

    private static boolean enabled() {
        return Boolean.getBoolean("elementalcircuits.elementalSmoke");
    }

    private static VisualTimeline timeline() {
        try {
            var f = SpellVisualClient.class.getDeclaredField("TIMELINE");
            f.setAccessible(true);
            return (VisualTimeline) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
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
            level.setDayTime(16000);
            level.setWeatherParameters(100000, 0, false, false);
            p.connection.teleport(7, -54.5, 12, 138.8f, 28f);
            p.getAbilities().mayfly = true;
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            for (int x = -12; x <= 12; x++)
                for (int z = -10; z <= 18; z++)
                    level.setBlock(
                            new BlockPos(x, -61, z),
                            Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
                            3);
            initialized = true;
        }
        if (command < 0 || command >= 8) return;
        if (command != ack) {
            targets.forEach(Entity::discard);
            targets.clear();
            for (int x = -2; x <= 2; x++)
                for (int y = -60; y <= -56; y++)
                    level.setBlock(new BlockPos(x, y, 8), Blocks.AIR.defaultBlockState(), 3);
            if (command == 0) {
                for (Vec3 at :
                        List.of(new Vec3(-2, -59, 3), new Vec3(1, -59, 4), new Vec3(3, -59, 6))) {
                    var c = EntityType.COW.create(level);
                    c.setPos(at);
                    c.setNoAi(true);
                    c.setNoGravity(true);
                    level.addFreshEntity(c);
                    targets.add(c);
                }
            }
            if (command == 4)
                for (int x = -2; x <= 2; x++)
                    for (int y = -60; y <= -56; y++)
                        level.setBlock(new BlockPos(x, y, 8), Blocks.STONE.defaultBlockState(), 3);
            ack = command;
            serverTicks = 0;
        }
        if (++serverTicks % 6 != 0) return;
        targets.forEach(
                c -> {
                    c.setHealth(c.getMaxHealth());
                    c.invulnerableTime = 0;
                });
        int index = command;
        var shape = SHAPES[index];
        var method =
                index == 3 || index == 5 || index == 6
                        ? SpellSpec.Method.AREA
                        : SpellSpec.Method.DIRECTED;
        var spec = new SpellSpec(method, shape, 0, 0, shape == SpellSpec.Shape.BEAM ? 0 : 3, 0);
        Vec3 origin =
                index == 0
                        ? new Vec3(-2, -58.3, 0)
                        : index == 1 || index == 2 ? new Vec3(0, -58.5, 1) : new Vec3(0, -58.5, 4);
        if (index == 5 || index == 6)
            origin = origin.add(0, ShapeResources.rules().groundOffset() / (double) Fixed.ONE, 0);
        SpellEffects.castOwned(
                level,
                p.getUUID(),
                origin,
                new Vec3(0, 0, 1),
                new UUID(901, 2026),
                new MagicCircuit.Cast(
                        serverTicks,
                        Mixture.pure(4 * Fixed.ONE).convert(ELEMENTS[index], Fixed.ONE),
                        spec),
                RuleResources.defaults(),
                Fixed.ONE);
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
            if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
            mc.options.pauseOnLostFocus = false;
            mc.options.guiScale().set(2);
            mc.options.renderDistance().set(4);
            mc.options.simulationDistance().set(5);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1440, 900);
            mc.resizeDisplay();
            mc.getLanguageManager().setSelected("zh_cn");
            mc.options.languageCode = "zh_cn";
            mc.reloadResourcePacks();
            stage = 0;
            ticks = 0;
            return;
        }
        if (mc.getOverlay() != null) return;
        if (++ticks > 1500) throw new AssertionError("元素特效GPU验证超时：" + stage);
        if (stage == 0 && ticks > 40 && !requested) {
            requested = true;
            var settings =
                    new LevelSettings(
                            "元素特效隔离验证",
                            GameType.CREATIVE,
                            false,
                            Difficulty.PEACEFUL,
                            true,
                            new GameRules(),
                            WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows()
                    .createFreshLevel(
                            "elements-" + System.currentTimeMillis(),
                            settings,
                            new WorldOptions(20261101, false, false),
                            a ->
                                    a.registryOrThrow(Registries.WORLD_PRESET)
                                            .getHolderOrThrow(WorldPresets.FLAT)
                                            .value()
                                            .createWorldDimensions(),
                            new TitleScreen());
            return;
        }
        if (stage == 0
                && initialized
                && mc.level != null
                && mc.player != null
                && mc.screen == null
                && mc.player.tickCount > 60
                && ++readyTicks > 20) {
            mc.options.hideGui = true;
            stage = 1;
            ticks = 0;
            command = 0;
            return;
        }
        if (stage >= 1 && stage <= 8) {
            int index = stage - 1;
            var kind =
                    index == 0
                            ? SpellVisual.Kind.BEAM
                            : SpellVisual.Kind.valueOf(SHAPES[index].name());
            if (ack != index
                    || ticks < 45
                    || SpellVisualRenderer.lastDraws() != 1
                    || timeline().frames().stream()
                            .noneMatch(f -> f.event().kind() == kind && f.age() < 2)) return;
            // Screenshot the preceding rendered frame, after the packet has had a frame to draw.
            if (index == 0
                    && timeline().frames().stream()
                                    .filter(
                                            f ->
                                                    f.event().kind() == SpellVisual.Kind.BEAM
                                                            && f.age() == 1)
                                    .count()
                            < 3) return;
            if (index == 0
                    && timeline().frames().stream()
                                    .filter(f -> f.event().kind() == SpellVisual.Kind.BEAM)
                                    .count()
                            < 3) throw new AssertionError("真实雷链没有同步三个服务端线段");
            Screenshot.grab(
                    mc.gameDirectory,
                    "element-" + NAMES[index] + ".png",
                    mc.getMainRenderTarget(),
                    m -> {});
            org.slf4j.LoggerFactory.getLogger("ElementalVisualSmoke")
                    .info(
                            "ELEMENT_GPU_CAPTURE: {} {} quads/{} draw",
                            NAMES[index],
                            SpellVisualRenderer.lastQuads(),
                            SpellVisualRenderer.lastDraws());
            command = stage;
            stage++;
            ticks = 0;
            return;
        }
        if (stage == 9 && ticks > 90) {
            if (!timeline().frames().isEmpty()) throw new AssertionError("停止施法后特效残留");
            mc.options.hideGui = false;
            mc.level.disconnect();
            mc.disconnect(new TitleScreen());
            stage = 10;
            ticks = 0;
            return;
        }
        if (stage == 10 && mc.level == null && ticks > 15) {
            if (!timeline().frames().isEmpty()) throw new AssertionError("退出后引用残留");
            mc.setScreen(new Gallery(0));
            stage = 11;
            ticks = 0;
            return;
        }
        if (stage >= 11 && stage <= 13 && ticks > 60) {
            Screenshot.grab(
                    mc.gameDirectory,
                    "element-gallery-" + (stage - 11) + ".png",
                    mc.getMainRenderTarget(),
                    m -> {});
            if (stage < 13) {
                mc.setScreen(new Gallery(stage - 10));
                stage++;
                ticks = 0;
            } else {
                org.slf4j.LoggerFactory.getLogger("ElementalVisualSmoke")
                        .info(
                                "ELEMENT_GPU_PASS: 8 authoritative combinations + actual chain"
                                    + " packets + expiry/logout + 3 animated GPU galleries, single"
                                    + " draw");
                stage = 14;
                ticks = 0;
                VisualBatchSmoke.begin(mc);
            }
        }
    }

    private static final class Gallery extends Screen {
        final int page;

        Gallery(int page) {
            super(Component.literal("元素与形状"));
            this.page = page;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float partial) {
            g.fill(0, 0, width, height, 0xff101823);
            g.drawString(
                    font,
                    "元素与形状 · " + new String[] {"投射物与光束", "屏障与扇面", "墙、环、柱与穹顶"}[page],
                    14,
                    12,
                    0xffe1bd78,
                    false);
            gallery.clear();
            var kinds =
                    page == 0
                            ? new SpellVisual.Kind[] {SpellVisual.Kind.TRAIL, SpellVisual.Kind.BEAM}
                            : page == 1
                                    ? new SpellVisual.Kind[] {
                                        SpellVisual.Kind.SHIELD, SpellVisual.Kind.FAN
                                    }
                                    : new SpellVisual.Kind[] {
                                        SpellVisual.Kind.WALL,
                                        SpellVisual.Kind.RING,
                                        SpellVisual.Kind.COLUMN,
                                        SpellVisual.Kind.DOME
                                    };
            double col = (width - 32) / 8.0,
                    row = (height - 100) / (double) kinds.length,
                    scale = page == 2 ? 15 : 24;
            for (int i = 0; i < 8; i++) {
                var element = ELEMENTS[i];
                g.drawCenteredString(
                        font,
                        Component.literal(
                                new String[] {"雷", "火", "水", "风", "土", "光", "暗", "奥术"}[i]),
                        (int) (16 + (i + .5) * col),
                        34,
                        VisualStyles.of(element).color());
                for (int j = 0; j < kinds.length; j++) {
                    var kind = kinds[j];
                    double x = (16 + (i + .5) * col - width / 2) / scale,
                            y = (70 + (j + .5) * row - height / 2) / scale;
                    var at = new SpellVisual.Point(x, y, -8);
                    var to =
                            kind == SpellVisual.Kind.BEAM || kind == SpellVisual.Kind.TRAIL
                                    ? new SpellVisual.Point(x, y - 1.5, -8)
                                    : new SpellVisual.Point(0, .45, .89302855497);
                    var e =
                            new SpellVisual(
                                    kind,
                                    element,
                                    at,
                                    to,
                                    kind == SpellVisual.Kind.BEAM || kind == SpellVisual.Kind.TRAIL
                                            ? 1
                                            : 1,
                                    8);
                    gallery.add(new VisualTimeline.Frame(e, 0, 12, ticks));
                }
            }
            g.flush();
            g.pose().pushPose();
            g.pose().translate(width / 2.0, height / 2.0, 1000);
            g.pose().scale((float) scale, (float) scale, (float) scale);
            SpellVisualRenderer.render(g.pose(), Vec3.ZERO, new Quaternionf(), gallery, partial);
            g.pose().popPose();
            if (SpellVisualRenderer.lastDraws() != 1 || SpellVisualRenderer.lastQuads() > 8192)
                throw new AssertionError("组合画廊合批失败");
            g.drawString(
                    font,
                    "Quads " + SpellVisualRenderer.lastQuads() + " · Draw 1 · CC0 素材适配",
                    14,
                    height - 18,
                    0xff81bdda,
                    false);
        }
    }
}
