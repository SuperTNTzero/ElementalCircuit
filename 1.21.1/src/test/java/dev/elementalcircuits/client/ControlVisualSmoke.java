package dev.elementalcircuits.client;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.resources.WandResources;
import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.client.screen.CircuitScreen;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.*;
import dev.elementalcircuits.network.CircuitNetwork;

import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.*;

/** Isolated real-widget tests; no world or production save is opened. */
@net.neoforged.fml.common.EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class ControlVisualSmoke {
    private static int stage = -1, ticks;
    private static CircuitScreen editor;

    private static <T> T field(String name, Class<T> type) {
        try {
            var f = CircuitScreen.class.getDeclaredField(name);
            f.setAccessible(true);
            return type.cast(f.get(editor));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void click(String label) {
        for (var c : List.copyOf(editor.children()))
            if (c instanceof AbstractWidget w && w.getMessage().getString().equals(label)) {
                editor.mouseClicked(w.getX() + 3, w.getY() + 3, 0);
                return;
            }
        throw new AssertionError("Missing control: " + label);
    }

    private static void capture(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), m -> {});
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("elementalcircuits.controlSmoke")) return;
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
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1440, 900);
            mc.resizeDisplay();
            var input = Rune.create(1, Rune.Kind.INPUT, new Hex(-2, 0));
            var filter = Rune.create(2, Rune.Kind.FILTER, new Hex(0, 0));
            var a = Rune.create(3, Rune.Kind.MANA_SEND, new Hex(1, 0));
            var b = Rune.create(4, Rune.Kind.MANA_SEND, new Hex(0, 1));
            var storage = Rune.create(5, Rune.Kind.STORAGE, new Hex(0, -1));
            var status = Rune.create(6, Rune.Kind.SIGNAL_SEND, new Hex(1, -1));
            var delay = Rune.create(7, Rune.Kind.DELAY, new Hex(-1, 1));
            var board =
                    new Blueprint(
                            5,
                            ServerSettings.circuitRules(),
                            List.of(input, filter, a, b, storage, status, delay),
                            List.of(
                                    new Route(
                                            1,
                                            2,
                                            Route.Payload.MANA,
                                            List.of(input.cell(), new Hex(-1, 0), filter.cell())),
                                    new Route(
                                            2,
                                            3,
                                            Route.Payload.MANA,
                                            List.of(filter.cell(), a.cell()))),
                            true);
            editor =
                    new CircuitScreen(
                            new CircuitNetwork.Editor(
                                    new UUID(52, 1),
                                    JsonCodecs.blueprint(board),
                                    false,
                                    0,
                                    true,
                                    "分流与观察",
                                    WandResources.definitions().basic(),
                                    0));
            mc.setScreen(editor);
            var model = field("model", EditorModel.class);
            model.selected = 2;
            model.expanded.clear();
            model.expanded.add(RuneCatalog.Group.FLOW);
            editor.resize(mc, editor.width, editor.height);
            stage = 0;
            ticks = 0;
            return;
        }
        if (++ticks > 600) throw new AssertionError("Control editor timeout");
        var model = field("model", EditorModel.class);
        if (stage == 0 && ticks > 15) {
            click("出口 2 · 其余成分");
            var layout = field("layout", EditorLayout.class);
            editor.mouseClicked(layout.x(new Hex(0, 1)), layout.y(new Hex(0, 1)), 0);
            if (editor.draftForExport().routes().stream()
                    .noneMatch(r -> r.source() == 2 && r.target() == 4 && r.sourcePort() == 1))
                throw new AssertionError("Named outlet did not wire through actual widgets");
            stage = 1;
            ticks = 0;
        } else if (stage == 1 && ticks > 15) {
            capture(mc, "element-filter");
            model.cancel();
            model.selected = 5;
            model.payload = Route.Payload.SIGNAL;
            editor.resize(mc, editor.width, editor.height);
            stage = 2;
            ticks = 0;
        } else if (stage == 2 && ticks > 15) {
            click("出口 3 · 已空");
            if (!model.drawing() || model.sourcePort != 2)
                throw new AssertionError("Storage outlet selection lost");
            stage = 3;
            ticks = 0;
        } else if (stage == 3 && ticks > 15) {
            capture(mc, "storage-status-outlets");
            model.cancel();
            model.selected = 7;
            editor.resize(mc, editor.width, editor.height);
            click("▸ " + RuneParameterEdit.title(Rune.Kind.DELAY));
            stage = 4;
            ticks = 0;
        } else if (stage == 4 && ticks > 12) {
            var box =
                    editor.children().stream()
                            .filter(c -> c instanceof EditBox)
                            .map(c -> (EditBox) c)
                            .findFirst()
                            .orElseThrow();
            box.setValue("37");
            editor.setFocused(box);
            editor.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
            if (editor.draftForExport().rune(7).value() != 37 * Fixed.ONE)
                throw new AssertionError("Delay parameter Enter save failed");
            capture(mc, "signal-delay");
            org.slf4j.LoggerFactory.getLogger("ControlSmoke")
                    .info(
                            "CONTROL_OBSERVATION_GPU_PASS: named mana/status outlets, actual canvas"
                                    + " wiring, delay parameter Enter save, static SVG glyphs");
            stage = 5;
            ticks = 0;
        } else if (stage == 5 && ticks > 10) {
            stage = 6;
            mc.stop();
        }
    }
}
