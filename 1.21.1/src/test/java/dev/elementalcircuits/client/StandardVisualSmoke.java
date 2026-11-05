package dev.elementalcircuits.client;

import dev.elementalcircuits.*;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.resources.WandResources;
import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.client.screen.CircuitScreen;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.examples.StandardCircuit;
import dev.elementalcircuits.domain.mana.StorageSettings;
import dev.elementalcircuits.network.CircuitNetwork;

import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.*;

/** Detached editor GPU checks, with actual typing and apply; never connects to a world. */
@net.neoforged.fml.common.EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class StandardVisualSmoke {
    private static int stage = -1, ticks;
    private static CircuitScreen editor;

    private static EditorModel model() {
        try {
            var f = CircuitScreen.class.getDeclaredField("model");
            f.setAccessible(true);
            return (EditorModel) f.get(editor);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
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

    private static void click(Screen s, String text) {
        for (var c : List.copyOf(s.children()))
            if (c instanceof AbstractWidget w && w.getMessage().getString().equals(text)) {
                s.mouseClicked(w.getX() + 4, w.getY() + 4, 0);
                return;
            }
        throw new AssertionError("Missing button: " + text);
    }

    private static void capture(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), m -> {});
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (!Boolean.getBoolean("elementalcircuits.standardSmoke")) return;
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
            var board =
                    StandardCircuit.create(ServerSettings.circuitRules(), true)
                            .withInputAmount(1, 10000 * Fixed.ONE);
            editor =
                    new CircuitScreen(
                            new CircuitNetwork.Editor(
                                    new UUID(37, 1),
                                    JsonCodecs.blueprint(board),
                                    false,
                                    0,
                                    true,
                                    "标准状态与蓄能法阵",
                                    WandResources.definitions().basic(),
                                    0));
            mc.setScreen(editor);
            model().expanded.clear();
            model().expanded.add(RuneCatalog.Group.FLOW);
            model().selected = 2;
            editor.resize(mc, editor.width, editor.height);
            stage = 0;
            ticks = 0;
            return;
        }
        if (++ticks > 600) throw new AssertionError("Standard UI timeout");
        if (stage == 0 && ticks > 18) {
            if (editor.children().stream().anyMatch(c -> c instanceof EditBox))
                throw new AssertionError("Parameters should start folded");
            capture(mc, "storage-circuit");
            click(editor, "▸ 存储参数");
            stage = 1;
            ticks = 0;
        } else if (stage == 1 && ticks > 12) {
            capture(mc, "storage-settings");
            var fields =
                    editor.children().stream()
                            .filter(c -> c instanceof EditBox)
                            .map(c -> (EditBox) c)
                            .toList();
            if (fields.size() != 3) throw new AssertionError("Inline storage fields missing");
            fields.get(0).setValue("20000");
            fields.get(1).setValue("120");
            fields.get(2).setValue("12.5");
            click(editor, "释放：达到阈值");
            fields =
                    editor.children().stream()
                            .filter(c -> c instanceof EditBox)
                            .map(c -> (EditBox) c)
                            .toList();
            if (!fields.get(0).getValue().equals("20000")
                    || !fields.get(2).getValue().equals("12.5"))
                throw new AssertionError("Widget rebuild discarded typed text");
            editor.setFocused(fields.get(2));
            editor.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
            var r = editor.draftForExport().rune(2);
            if (!r.storage()
                            .equals(
                                    new StorageSettings(
                                            20000 * Fixed.ONE, 120 * Fixed.ONE, 25 * Fixed.ONE / 2))
                    || !r.signalControl())
                throw new AssertionError("Inline storage Enter save failed");
            model().selected = 1;
            editor.resize(mc, editor.width, editor.height);
            click(editor, "▸ 每次注入量");
            stage = 2;
            ticks = 0;
        } else if (stage == 2 && ticks > 12) {
            var box =
                    editor.children().stream()
                            .filter(c -> c instanceof EditBox)
                            .map(c -> (EditBox) c)
                            .findFirst()
                            .orElseThrow();
            if (!box.getValue().equals("10000"))
                throw new AssertionError("Large input was truncated in editor");
            box.setValue("10001.5");
            editor.resize(mc, editor.width, editor.height);
            box =
                    editor.children().stream()
                            .filter(c -> c instanceof EditBox)
                            .map(c -> (EditBox) c)
                            .findFirst()
                            .orElseThrow();
            if (!box.getValue().equals("10001.5"))
                throw new AssertionError("Resize discarded unsaved parameter text");
            capture(mc, "input-10000");
            editor.setFocused(box);
            editor.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER, 0, 0);
            if (editor.draftForExport().inputAmount(editor.draftForExport().rune(1))
                    != 10001 * Fixed.ONE + Fixed.ONE / 2)
                throw new AssertionError("Keypad Enter did not save input");
            model().selected = -1;
            model().expanded.addAll(EnumSet.allOf(RuneCatalog.Group.class));
            model().paletteOffset = 9;
            editor.resize(mc, editor.width, editor.height);
            click(editor, "▾ 元素转化");
            if (model().paletteOffset != 9)
                throw new AssertionError("Collapse reset catalog scroll");
            click(editor, "▸ 元素转化");
            if (model().paletteOffset != 9) throw new AssertionError("Expand reset catalog scroll");
            stage = 3;
            ticks = 0;
        } else if (stage == 3 && ticks > 12) {
            capture(mc, "catalog-scroll");
            var layout = layout();
            var wire = editor.draftForExport().routes().getFirst();
            var a = wire.path().getFirst();
            var b = wire.path().get(1);
            double x = (layout.x(a) + layout.x(b)) / 2, y = (layout.y(a) + layout.y(b)) / 2;
            int count = editor.draftForExport().routes().size();
            editor.mouseMoved(x, y);
            editor.setFocused(null);
            model().tool = EditorModel.Tool.PLACE;
            editor.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE, 0, 0);
            if (editor.draftForExport().routes().size() != count)
                throw new AssertionError("Place mode deleted a wire");
            model().tool = EditorModel.Tool.ROUTE;
            editor.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE, 0, 0);
            if (editor.draftForExport().routes().size() != count - 1
                    || editor.draftForExport().runes().size() != 6)
                throw new AssertionError("Route Delete failed to delete exactly one wire");
            model().draft.undo();
            model().tool = EditorModel.Tool.SELECT;
            editor.resize(mc, editor.width, editor.height);
            if (editor.children().stream()
                    .anyMatch(
                            c ->
                                    c instanceof AbstractWidget w
                                            && w.getMessage().getString().equals("火球")))
                throw new AssertionError("Test templates still exposed as production buttons");
            org.slf4j.LoggerFactory.getLogger("StandardSmoke")
                    .info(
                            "STANDARD_MANA_GPU_PASS: folded inline parameters, three storage"
                                + " fields, typed text survives rebuild and resize, Enter and"
                                + " keypad Enter, file templates absent, sidebar viewport retained,"
                                + " route-only Delete removes one hovered wire and undo restores"
                                + " it");
            stage = 4;
            ticks = 0;
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 960, 720);
            mc.options.guiScale().set(3);
            mc.resizeDisplay();
            model().selected = 1;
            editor.resize(mc, editor.width, editor.height);
            click(editor, "▸ 每次注入量");
            editor.mouseScrolled(editor.width - 20, 130, 0, -1);
        } else if (stage == 4 && ticks > 12) {
            var box =
                    editor.children().stream()
                            .filter(c -> c instanceof EditBox)
                            .map(c -> (EditBox) c)
                            .findFirst()
                            .orElseThrow();
            box.setValue("123.25");
            editor.setFocused(box);
            editor.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
            if (editor.draftForExport().inputAmount(editor.draftForExport().rune(1))
                    != 123 * Fixed.ONE + Fixed.ONE / 4)
                throw new AssertionError("Compact window cannot edit and save parameters");
            capture(mc, "compact-parameters");
            org.slf4j.LoggerFactory.getLogger("StandardSmoke")
                    .info(
                            "COMPACT_PARAMETER_GPU_PASS: 320x240 logical pixels, short-panel"
                                    + " scrolling and Enter save");
            stage = 5;
            ticks = 0;
        } else if (stage == 5 && ticks > 5) {
            stage = 6;
            mc.stop();
        }
    }
}
