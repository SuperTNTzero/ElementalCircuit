package dev.elementalcircuits.client.render;

import dev.elementalcircuits.client.editor.BlueprintTransferLayout;

import net.minecraft.client.gui.*;
import net.minecraft.network.chat.Component;

/** Read-only subpage painting, separate from validation, clipboard and network. */
public final class BlueprintRenderer {
    private BlueprintRenderer() {}

    public static void paint(
            GuiGraphics g,
            Font font,
            BlueprintTransferLayout l,
            boolean exporting,
            String message) {
        int x = l.left(), w = l.boxWidth();
        WorkshopSkin.background(g, l.width(), l.height());
        WorkshopSkin.panel(g, x - 4, 28, w + 8, l.height() - 71, WorkshopSkin.PANEL);
        WorkshopSkin.title(
                g,
                font,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.render._blueprint_renderer.001"),
                x,
                10);
        String hint =
                exporting
                        ? dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.render._blueprint_renderer.002")
                        : dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.render._blueprint_renderer.003");
        g.drawString(font, font.plainSubstrByWidth(hint, w), x, 61, EditorRenderer.TEXT, false);
        g.drawString(
                font,
                font.plainSubstrByWidth(
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.render._blueprint_renderer.004"),
                        w),
                x,
                75,
                EditorRenderer.MUTED,
                false);
        int y = l.top() + l.boxHeight() + 18;
        var lines = font.split(Component.literal(message == null ? "" : message), w);
        for (int i = 0; i < Math.min(2, lines.size()); i++)
            g.drawString(font, lines.get(i), x, y + i * 10, EditorRenderer.BLUE, false);
    }
}
