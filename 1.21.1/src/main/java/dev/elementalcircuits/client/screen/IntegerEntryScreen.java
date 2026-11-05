package dev.elementalcircuits.client.screen;

import dev.elementalcircuits.client.render.*;

import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/** Small numeric editor used for trigger timing and budget share. */
public final class IntegerEntryScreen extends Screen {
    private final Screen parent;
    private final int value, min, max;
    private final IntConsumer accept;
    private EditBox field;
    private String error = "";

    public IntegerEntryScreen(
            Screen parent, String title, int value, int min, int max, IntConsumer accept) {
        super(Component.literal(title));
        this.parent = parent;
        this.value = value;
        this.min = min;
        this.max = max;
        this.accept = accept;
    }

    @Override
    protected void init() {
        int x = width / 2 - 95, y = height / 2 - 20;
        field = new EditBox(font, x, y, 190, 22, title);
        field.setMaxLength(8);
        field.setValue(Integer.toString(value));
        addRenderableWidget(field);
        setInitialFocus(field);
        addRenderableWidget(
                new CircuitButton(
                        x,
                        y + 32,
                        91,
                        22,
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._integer_entry_screen.001"),
                        false,
                        null,
                        false,
                        this::apply));
        addRenderableWidget(
                new CircuitButton(
                        x + 99,
                        y + 32,
                        91,
                        22,
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._integer_entry_screen.002"),
                        false,
                        null,
                        false,
                        this::onClose));
    }

    private void apply() {
        try {
            int number = Integer.parseInt(field.getValue());
            if (number < min || number > max) throw new IllegalArgumentException();
            accept.accept(number);
            onClose();
        } catch (RuntimeException e) {
            error =
                    dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.screen._integer_entry_screen.003")
                            + min
                            + "–"
                            + max;
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
            apply();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void renderBackground(GuiGraphics g, int x, int y, float partial) {}

    @Override
    public void render(GuiGraphics g, int x, int y, float partial) {
        WorkshopSkin.background(g, width, height);
        WorkshopSkin.title(g, font, title.getString(), width / 2 - 95, height / 2 - 47);
        g.drawString(font, error, width / 2 - 95, height / 2 + 43, WorkshopSkin.GOLD, false);
        super.render(g, x, y, partial);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
