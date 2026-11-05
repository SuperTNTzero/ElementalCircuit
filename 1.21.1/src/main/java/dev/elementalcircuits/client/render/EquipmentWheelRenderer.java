package dev.elementalcircuits.client.render;

import dev.elementalcircuits.server.equipment.EquipmentCasting;

import net.minecraft.client.gui.*;

import java.util.*;

/** Pure observation painting. Selection and triggering remain screen/server responsibilities. */
public final class EquipmentWheelRenderer {
    public static void paint(
            GuiGraphics g,
            Font font,
            int width,
            int height,
            List<EquipmentCasting.Entry> entries,
            int hover,
            int page,
            int pages) {
        g.fill(0, 0, width, height, 0xa609101d);
        int cx = width / 2,
                cy = height / 2,
                r = Math.max(32, Math.min(120, Math.min(width * 36 / 100, height * 32 / 100)));
        g.drawCenteredString(
                font,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.render._equipment_wheel_renderer.001"),
                cx,
                cy - 16,
                0xffdbc38b);
        g.drawCenteredString(
                font,
                entries.isEmpty()
                        ? dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.render._equipment_wheel_renderer.002")
                        : dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.render._equipment_wheel_renderer.003"),
                cx,
                cy + 2,
                0xff91b7d9);
        for (int i = 0; i < entries.size(); i++) {
            var e = entries.get(i);
            double angle = -Math.PI / 2 + Math.PI * 2 * i / entries.size();
            int x = cx + (int) (Math.cos(angle) * r), y = cy + (int) (Math.sin(angle) * r);
            int w =
                    entries.size() > 4
                            ? Math.max(40, Math.min(90, r * 69 / 100))
                            : Math.min(100, (width - 12) / 3);
            g.fill(
                    x - w / 2 - 1,
                    y - 19,
                    x + w / 2 + 1,
                    y + 20,
                    i == hover ? 0xffdfbc70 : e.selected() ? 0xff7ca4bf : 0xff6a604a);
            g.fill(x - w / 2, y - 18, x + w / 2, y + 19, i == hover ? 0xff183c5e : 0xf0102135);
            g.drawCenteredString(
                    font,
                    font.plainSubstrByWidth(
                            net.minecraft.network.chat.Component.translatable(e.item()).getString(),
                            w - 8),
                    x,
                    y - 12,
                    0xffe4dcc9);
            g.drawCenteredString(
                    font,
                    dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.render._equipment_wheel_renderer.004")
                            + e.slot()
                            + " · "
                            + (e.trigger() == EquipmentCasting.Trigger.HURT
                                    ? dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.render._equipment_wheel_renderer.005")
                                    : dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.render._equipment_wheel_renderer.006")),
                    x,
                    y + 1,
                    0xff85ccfa);
        }
        g.drawCenteredString(
                font,
                dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.render._equipment_wheel_renderer.007")
                        + (page + 1)
                        + " / "
                        + Math.max(1, pages),
                cx,
                height - 28,
                0xffb6c4d1);
    }

    private EquipmentWheelRenderer() {}
}
