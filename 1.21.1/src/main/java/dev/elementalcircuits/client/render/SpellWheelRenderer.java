package dev.elementalcircuits.client.render;

import dev.elementalcircuits.application.observation.CircuitSelection;

import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.RenderType;

/** Pure radial presentation; all choices and network actions are supplied by the screen. */
public final class SpellWheelRenderer {
    private SpellWheelRenderer() {}

    private static void sector(
            GuiGraphics g,
            double cx,
            double cy,
            double inner,
            double outer,
            double a,
            double b,
            int color) {
        // GUI projection flips Y: keep the same front-face winding as GuiGraphics.fill.
        var matrix = g.pose().last().pose();
        var out = g.bufferSource().getBuffer(RenderType.gui());
        int steps = Math.max(2, (int) Math.ceil((b - a) * 18));
        for (int i = 0; i < steps; i++) {
            double from = a + (b - a) * i / steps, to = a + (b - a) * (i + 1) / steps;
            out.addVertex(
                            matrix,
                            (float) (cx + Math.cos(to) * inner),
                            (float) (cy + Math.sin(to) * inner),
                            0)
                    .setColor(color);
            out.addVertex(
                            matrix,
                            (float) (cx + Math.cos(to) * outer),
                            (float) (cy + Math.sin(to) * outer),
                            0)
                    .setColor(color);
            out.addVertex(
                            matrix,
                            (float) (cx + Math.cos(from) * outer),
                            (float) (cy + Math.sin(from) * outer),
                            0)
                    .setColor(color);
            out.addVertex(
                            matrix,
                            (float) (cx + Math.cos(from) * inner),
                            (float) (cy + Math.sin(from) * inner),
                            0)
                    .setColor(color);
        }
    }

    private static void centred(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawString(font, text, x - font.width(text) / 2, y, color, false);
    }

    public static void paint(
            GuiGraphics g,
            Font font,
            int width,
            int height,
            double radius,
            CircuitSelection data,
            int hover) {
        g.fill(0, 0, width, height, 0x5510161c);
        int cx = width / 2, cy = height / 2;
        if (data == null) {
            centred(
                    g,
                    font,
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.render._spell_wheel_renderer.001"),
                    cx,
                    cy,
                    WorkshopSkin.TEXT);
            return;
        }
        centred(
                g,
                font,
                (data.slots().stream().anyMatch(CircuitSelection.Slot::book)
                                ? dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.render._spell_wheel_renderer.002")
                                : dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.render._spell_wheel_renderer.003"))
                        + "  ["
                        + (data.book()
                                ? dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.render._spell_wheel_renderer.004")
                                : dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.render._spell_wheel_renderer.005"))
                        + data.selected()
                        + "]",
                cx,
                (int) (cy - radius - 27),
                WorkshopSkin.GOLD);
        int count = data.slots().size();
        double step = Math.PI * 2 / count;
        for (int i = 0; i < count; i++) {
            var slot = data.slots().get(i);
            double angle = -Math.PI / 2 + step * i,
                    a = angle - step / 2 + .035,
                    b = angle + step / 2 - .035;
            boolean hot = i == hover;
            sector(g, cx, cy, 25, radius, a, b, hot ? 0xf0394c55 : 0xe01c292e);
            sector(g, cx, cy, radius - 1.4, radius, a, b, hot ? WorkshopSkin.GOLD : 0xff655d49);
            if (data.selected(slot)) sector(g, cx, cy, 25, 28, a, b, WorkshopSkin.GOLD);
            int x = (int) (cx + Math.cos(angle) * radius * .68),
                    y = (int) (cy + Math.sin(angle) * radius * .68);
            GlyphAtlas.draw(
                    g,
                    "element/" + slot.element().name(),
                    x,
                    y - 5,
                    9,
                    0xff000000 | slot.element().color);
            centred(
                    g,
                    font,
                    (slot.book()
                                    ? dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.render._spell_wheel_renderer.004")
                                    : dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.render._spell_wheel_renderer.005"))
                            + String.format(java.util.Locale.ROOT, "%02d", slot.id()),
                    x,
                    y + 9,
                    hot || data.selected(slot) ? WorkshopSkin.GOLD : WorkshopSkin.TEXT);
        }
        centred(
                g,
                font,
                hover < 0
                        ? dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.render._spell_wheel_renderer.006")
                        : (data.slots().get(hover).book()
                                        ? dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.render._spell_wheel_renderer.004")
                                        : dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.render._spell_wheel_renderer.005"))
                                + " · "
                                + data.slots().get(hover).id(),
                cx,
                cy - 3,
                WorkshopSkin.GOLD);
        centred(
                g,
                font,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.render._spell_wheel_renderer.007"),
                cx,
                (int) (cy + radius + 17),
                WorkshopSkin.MUTED);
    }
}
