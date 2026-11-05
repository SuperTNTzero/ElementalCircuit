package dev.elementalcircuits.client.screen;

import dev.elementalcircuits.client.editor.EquipmentWheelModel;
import dev.elementalcircuits.client.input.EquipmentClient;
import dev.elementalcircuits.client.render.EquipmentWheelRenderer;
import dev.elementalcircuits.network.EquipmentNetwork;
import dev.elementalcircuits.server.equipment.EquipmentCasting;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.*;

public final class EquipmentWheelScreen extends Screen {
    private List<EquipmentCasting.Entry> entries = List.of();
    private int page, hover = -1;
    private boolean closed;

    public EquipmentWheelScreen() {
        super(
                dev.elementalcircuits.client.text.UiText.component(
                        "screen.elementalcircuits.equipment_wheel"));
    }

    public void accept(List<EquipmentCasting.Entry> entries) {
        this.entries = List.copyOf(entries);
        page = Math.min(page, Math.max(0, (entries.size() - 1) / 8));
    }

    private List<EquipmentCasting.Entry> visible() {
        return entries.subList(page * 8, Math.min(entries.size(), page * 8 + 8));
    }

    private EquipmentCasting.Entry hovered() {
        var v = visible();
        return hover >= 0 && hover < v.size() ? v.get(hover) : null;
    }

    private void close(boolean choose) {
        if (closed) return;
        closed = true;
        var entry = hovered();
        if (choose && entry != null) EquipmentNetwork.send(entry.identity(), entry.slot(), 2);
        minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        close(false);
    }

    @Override
    public void tick() {
        if (minecraft.player == null || !minecraft.player.isAlive()) close(false);
    }

    @Override
    public void mouseMoved(double x, double y) {
        hover = EquipmentWheelModel.hover(x - width / 2.0, y - height / 2.0, visible().size());
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (EquipmentClient.wheelMatches(key, scan)) {
            close(true);
            return true;
        }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (EquipmentClient.wheelMouse(button)) {
            close(true);
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        var e = hovered();
        if (button == 0) {
            close(true);
            return true;
        }
        if (button == 1 && e != null) {
            EquipmentNetwork.send(e.identity(), e.slot(), 3);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0) {
            page =
                    Math.floorMod(
                            page + (vertical > 0 ? -1 : 1), Math.max(1, (entries.size() + 7) / 8));
            hover = -1;
        }
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int x, int y, float partial) {}

    @Override
    public void render(GuiGraphics g, int x, int y, float partial) {
        EquipmentWheelRenderer.paint(
                g, font, width, height, visible(), hover, page, (entries.size() + 7) / 8);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
