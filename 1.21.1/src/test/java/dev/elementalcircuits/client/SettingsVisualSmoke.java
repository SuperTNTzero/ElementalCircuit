package dev.elementalcircuits.client;

import dev.elementalcircuits.*;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.adapter.resources.WandResources;
import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.client.screen.CircuitScreen;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.network.*;
import dev.elementalcircuits.server.*;

import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.io.File;
import java.util.*;

@net.neoforged.fml.common.EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class SettingsVisualSmoke {
    private static int stage = -1, ticks;
    private static volatile boolean initialized;
    private static CircuitScreen editor;

    private static boolean enabled() {
        return Boolean.getBoolean("elementalcircuits.settingsSmoke");
    }

    private static void capture(Minecraft mc, String name) {
        File dir = new File("build/settings-captures");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new AssertionError("截图目录创建失败");
        Screenshot.grab(dir, name + ".png", mc.getMainRenderTarget(), c -> {});
    }

    private static EditorLayout layout() {
        try {
            var f = CircuitScreen.class.getDeclaredField("layout");
            f.setAccessible(true);
            return (EditorLayout) f.get(editor);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static List<Button> buttons(GuiEventListener node) {
        var result = new ArrayList<Button>();
        if (node instanceof Button b) result.add(b);
        if (node instanceof ContainerEventHandler c)
            for (var child : c.children()) result.addAll(buttons(child));
        return result;
    }

    @SubscribeEvent
    public static void world(LevelTickEvent.Post event) {
        if (!enabled()
                || initialized
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || level.dimension() != Level.OVERWORLD
                || level.players().isEmpty()) return;
        var p = level.players().getFirst();
        var source = p.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        try {
            var d = level.getServer().getCommands().getDispatcher();
            d.execute("ec mana capacity @s 2048", source);
            d.execute("ec mana set @s 512", source);
            d.execute("ec mana regen @s 0", source);
            initialized = true;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!enabled()) return;
        var mc = Minecraft.getInstance();
        mc.getToasts().clear();
        if (stage < 0) {
            if (mc.screen instanceof AccessibilityOnboardingScreen) {
                mc.options.onboardAccessibility = false;
                mc.setScreen(new TitleScreen());
            }
            if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
            mc.options.pauseOnLostFocus = false;
            mc.options.guiScale().set(2);
            mc.options.renderDistance().set(3);
            mc.options.simulationDistance().set(5);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 800);
            mc.resizeDisplay();
            mc.getLanguageManager().setSelected("zh_cn");
            mc.options.languageCode = "zh_cn";
            mc.reloadResourcePacks();
            stage = 0;
            ticks = 0;
            return;
        }
        if (mc.getOverlay() != null) return;
        if (++ticks > 1200) throw new AssertionError("设置图形验证超时 " + stage);
        if (stage == 0 && ticks > 30) {
            var rules = RuleResources.defaults().withBoards(new Rules.BoardSizes(64, 64, 64));
            List<Rune> nodes = new ArrayList<>();
            int id = 1;
            for (int q = -7; q <= 7; q++)
                for (int r = -7; r <= 7; r++)
                    if (new Hex(q, r).inside(7))
                        nodes.add(Rune.create(id++, Rune.Kind.OR, new Hex(q, r)));
            nodes.add(
                    new Rune(
                            2048,
                            Rune.Kind.COUNTER,
                            new Hex(40, 0),
                            Element.PURE,
                            1000000L * Fixed.ONE,
                            true,
                            false,
                            List.of()));
            editor =
                    new CircuitScreen(
                            new CircuitNetwork.Editor(
                                    UUID.randomUUID(),
                                    JsonCodecs.blueprint(
                                            new Blueprint(5, rules, nodes, List.of(), true)),
                                    false,
                                    0,
                                    false,
                                    "大格盘 · 滚轮缩放 / 中键平移 / Home复位",
                                    WandResources.definitions().basic(),
                                    0));
            mc.setScreen(editor);
            stage = 1;
            ticks = 0;
            return;
        }
        if (stage == 1 && ticks > 15) {
            var l = layout();
            editor.mouseScrolled(l.cx(), l.cy(), 0, 4);
            l = layout();
            double x = l.width() / 2.0, y = (l.top() + l.bottom()) / 2.0;
            editor.mouseClicked(x, y, 2);
            editor.mouseDragged(x, y, 2, x - l.x(new Hex(40, 0)), y - l.y(new Hex(40, 0)));
            editor.mouseReleased(x, y, 2);
            editor.mouseClicked(x, y, 0);
            editor.mouseReleased(x, y, 0);
            stage = 2;
            ticks = 0;
            return;
        }
        if (stage == 2 && ticks > 15) {
            if (editor.children().stream().noneMatch(EditBox.class::isInstance))
                throw new AssertionError("大格盘平移后无法选中远处符文");
            capture(mc, "large-board-counter");
            mc.setScreen(new TitleScreen());
            var settings =
                    new LevelSettings(
                            "配置隔离验证",
                            GameType.CREATIVE,
                            false,
                            Difficulty.PEACEFUL,
                            true,
                            new GameRules(),
                            WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows()
                    .createFreshLevel(
                            "settings-" + System.currentTimeMillis(),
                            settings,
                            new WorldOptions(20261101, false, false),
                            a ->
                                    a.registryOrThrow(Registries.WORLD_PRESET)
                                            .getHolderOrThrow(WorldPresets.FLAT)
                                            .value()
                                            .createWorldDimensions(),
                            new TitleScreen());
            stage = 3;
            ticks = 0;
            return;
        }
        if (stage == 3
                && initialized
                && mc.level != null
                && mc.player != null
                && mc.screen == null
                && mc.player.tickCount > 25) {
            try {
                var f = ClientHooks.class.getDeclaredField("capacity");
                f.setAccessible(true);
                if (f.getLong(null) != 2048 * Fixed.ONE) return;
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            capture(mc, "admin-mana-2048");
            var config =
                    net.neoforged.fml.config.ModConfigs.getConfigSet(
                                    net.neoforged.fml.config.ModConfig.Type.SERVER)
                            .stream()
                            .filter(c -> c.getModId().equals(ElementalCircuits.ID))
                            .findFirst()
                            .orElseThrow();
            mc.setScreen(
                    new net.neoforged.neoforge.client.gui.ConfigurationScreen
                            .ConfigurationSectionScreen(
                            new TitleScreen(),
                            config.getType(),
                            config,
                            Component.literal("服务端设置")));
            stage = 4;
            ticks = 0;
            return;
        }
        if (stage == 4 && ticks > 15) {
            capture(mc, "server-config-groups");
            var list = buttons(mc.screen);
            var b =
                    list.stream()
                            .filter(
                                    x ->
                                            !x.getMessage().getString().contains("完成")
                                                    && !x.getMessage().getString().contains("取消")
                                                    && !x.getMessage().getString().contains("Done"))
                            .findFirst()
                            .orElseThrow();
            b.onPress();
            stage = 5;
            ticks = 0;
            return;
        }
        if (stage == 5 && ticks > 15) {
            capture(mc, "server-config-options");
            mc.level.disconnect();
            mc.disconnect(new TitleScreen());
            stage = 6;
            ticks = 0;
            return;
        }
        if (stage == 6 && ticks > 20) {
            for (String name :
                    List.of(
                            "large-board-counter",
                            "admin-mana-2048",
                            "server-config-groups",
                            "server-config-options"))
                if (!new File("build/settings-captures/screenshots/" + name + ".png").isFile())
                    throw new AssertionError("缺少截图：" + name);
            org.slf4j.LoggerFactory.getLogger("SettingsVisualSmoke")
                    .info(
                            "SETTINGS_GPU_PASS: large board pan/zoom and remote selection, operator"
                                    + " commands and 2048 mana HUD, native server config"
                                    + " groups/options; four screenshots saved");
            mc.stop();
        }
    }
}
