package dev.elementalcircuits.client.render;

import dev.elementalcircuits.application.observation.AssemblyInspection;
import dev.elementalcircuits.domain.Fixed;

import net.minecraft.client.gui.*;
import net.minecraft.network.chat.Component;

/** Read-only management page painting. */
public final class AssemblyRenderer {
    private AssemblyRenderer() {}

    public static void slot(
            GuiGraphics g,
            Font font,
            int x,
            int y,
            int width,
            AssemblyInspection.Slot slot,
            boolean selected,
            boolean hovered) {
        WorkshopSkin.panel(
                g,
                x,
                y,
                width,
                38,
                selected
                        ? WorkshopSkin.SELECTED
                        : hovered ? WorkshopSkin.HOVER : WorkshopSkin.BUTTON);
        String number = String.format(java.util.Locale.ROOT, "%02d", slot.id());
        g.pose().pushPose();
        g.pose().translate(x + 9, y + 9, 0);
        g.pose().scale(1.6f, 1.6f, 1);
        g.drawString(font, number, 0, 0, selected ? WorkshopSkin.GOLD : WorkshopSkin.TEXT, false);
        g.pose().popPose();
        String material =
                switch (slot.material()) {
                    case WOOD ->
                            dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.render._assembly_renderer.001");
                    case BRASS ->
                            dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.render._assembly_renderer.002");
                    case CRYSTAL ->
                            dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.render._assembly_renderer.003");
                };
        g.drawString(
                font,
                dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.render._assembly_renderer.004")
                        + slot.id()
                        + " · "
                        + material
                        + (selected
                                ? dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.render._assembly_renderer.005")
                                : ""),
                x + 42,
                y + 7,
                selected ? WorkshopSkin.GOLD : WorkshopSkin.TEXT,
                false);
        String status =
                (slot.melted()
                                ? dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.render._assembly_renderer.006")
                                : slot.boardMaximum() > 0 && slot.boardRemaining() == 0
                                        ? dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.render._assembly_renderer.007")
                                        : slot.powered()
                                                ? dev.elementalcircuits.client.text.UiText.string(
                                                        "text.elementalcircuits.client.render._assembly_renderer.008")
                                                : dev.elementalcircuits.client.text.UiText.string(
                                                        "text.elementalcircuits.client.render._assembly_renderer.009"))
                        + dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.render._assembly_renderer.010")
                        + Fixed.display(slot.boardRemaining())
                        + " / "
                        + Fixed.display(slot.boardMaximum())
                        + dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.render._assembly_renderer.011")
                        + Fixed.display(slot.boardCapacity());
        g.drawString(
                font,
                font.plainSubstrByWidth(status, width - 50),
                x + 42,
                y + 22,
                WorkshopSkin.MUTED,
                false);
    }

    public static void paint(
            GuiGraphics g,
            Font font,
            int width,
            int height,
            int left,
            int span,
            String hint,
            String message) {
        paint(
                g,
                font,
                width,
                height,
                left,
                span,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.render._assembly_renderer.012"),
                hint,
                message);
    }

    public static void paint(
            GuiGraphics g,
            Font font,
            int width,
            int height,
            int left,
            int span,
            String title,
            String hint,
            String message) {
        WorkshopSkin.background(g, width, height);
        WorkshopSkin.panel(g, left - 4, 28, span + 8, height - 91, WorkshopSkin.PANEL);
        WorkshopSkin.title(g, font, title, left, 10);
        if (hint != null && !hint.isBlank())
            g.drawString(
                    font,
                    font.plainSubstrByWidth(hint, span),
                    left,
                    59,
                    EditorRenderer.MUTED,
                    false);
        var lines =
                font.split(
                        Component.literal(message == null ? "" : message), Math.max(40, span - 62));
        for (int i = 0; i < Math.min(1, lines.size()); i++)
            g.drawString(
                    font, lines.get(i), left, height - 52 + i * 10, EditorRenderer.BLUE, false);
    }
}
