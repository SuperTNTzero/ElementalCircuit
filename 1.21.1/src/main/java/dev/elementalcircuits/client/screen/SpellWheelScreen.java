package dev.elementalcircuits.client.screen;

import dev.elementalcircuits.application.observation.CircuitSelection;
import dev.elementalcircuits.client.editor.SpellWheelModel;
import dev.elementalcircuits.client.input.BookClient;
import dev.elementalcircuits.client.render.SpellWheelRenderer;
import dev.elementalcircuits.network.BookNetwork;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/** Held-key modal: stale replies cannot open it, and changing the physical carrier cancels it. */
public final class SpellWheelScreen extends Screen {
    private final UUID identity;
    private final UUID bookIdentity;
    private CircuitSelection data;
    private int hover = -1;
    private boolean closed;

    public SpellWheelScreen(UUID identity) {
        super(
                dev.elementalcircuits.client.text.UiText.component(
                        "screen.elementalcircuits.spell_wheel"));
        this.identity = identity;
        this.bookIdentity = BookClient.bookIdentity();
    }

    @Override
    protected void init() {
        GLFW.glfwSetCursorPos(
                minecraft.getWindow().getWindow(),
                minecraft.getWindow().getScreenWidth() / 2.0,
                minecraft.getWindow().getScreenHeight() / 2.0);
    }

    public void accept(CircuitSelection value) {
        if (value.identity().equals(identity)) {
            data = value;
            hover = -1;
        }
    }

    public CircuitSelection data() {
        return data;
    }

    private void close(boolean commit) {
        if (closed) return;
        closed = true;
        if (commit && data != null && hover >= 0)
            BookNetwork.send(
                    new BookNetwork.SpellRequest(
                            identity,
                            data.slots().get(hover).carrier(),
                            7,
                            data.slots().get(hover).id()));
        minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        close(false);
    }

    @Override
    public void tick() {
        if (!BookClient.carrierIdentity().equals(identity)
                || !BookClient.bookIdentity().equals(bookIdentity)) close(false);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (BookClient.wheelMatches(key, scan)) {
            close(true);
            return true;
        }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (BookClient.wheelMouse(button)) {
            close(true);
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close(false);
            return true;
        }
        if (data != null && key >= GLFW.GLFW_KEY_1 && key < GLFW.GLFW_KEY_1 + data.slots().size()) {
            hover = key - GLFW.GLFW_KEY_1;
            close(true);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) {
            close(true);
            return true;
        }
        if (button == 1) {
            close(false);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double amount) {
        if (data != null && amount != 0)
            hover =
                    Math.floorMod(
                            (hover < 0 ? data.slots().indexOf(data.current()) : hover)
                                    + (amount > 0 ? -1 : 1),
                            data.slots().size());
        return true;
    }

    @Override
    public void mouseMoved(double x, double y) {
        if (data != null)
            hover = SpellWheelModel.hover(x - width / 2.0, y - height / 2.0, data.slots().size());
        super.mouseMoved(x, y);
    }

    @Override
    public void renderBackground(GuiGraphics g, int x, int y, float partial) {}

    @Override
    public void render(GuiGraphics g, int x, int y, float partial) {
        g.drawManaged(
                () ->
                        SpellWheelRenderer.paint(
                                g,
                                font,
                                width,
                                height,
                                SpellWheelModel.radius(width, height),
                                data,
                                hover));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
