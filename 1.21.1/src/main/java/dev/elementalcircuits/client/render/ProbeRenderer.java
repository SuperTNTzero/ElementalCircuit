package dev.elementalcircuits.client.render;

import dev.elementalcircuits.application.observation.SurveyReport;

import net.minecraft.client.gui.*;
import net.minecraft.network.chat.Component;

/** Pure report painting; rows scroll independently of title, progress and close action. */
public final class ProbeRenderer {
    private ProbeRenderer() {}

    public static int visible(int height) {
        return Math.max(1, (height - 143) / 27);
    }

    public static void paint(
            GuiGraphics g, Font f, int width, int height, SurveyReport report, int offset) {
        int span = Math.min(560, width - 16), left = (width - span) / 2;
        WorkshopSkin.background(g, width, height);
        WorkshopSkin.title(
                g,
                f,
                dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.render._probe_renderer.001")
                        + report.title(),
                left,
                10);
        g.drawString(
                f,
                f.plainSubstrByWidth(report.location(), span),
                left,
                35,
                WorkshopSkin.MUTED,
                false);
        int limit = Math.min(report.rows().size(), offset + visible(height));
        for (int i = offset; i < limit; i++) {
            var row = report.rows().get(i);
            int y = 54 + (i - offset) * 27;
            WorkshopSkin.panel(g, left, y, span, 25, WorkshopSkin.PANEL);
            int key = Math.min(112, span / 3);
            g.drawString(
                    f,
                    f.plainSubstrByWidth(row.name(), key - 10),
                    left + 8,
                    y + 8,
                    WorkshopSkin.MUTED,
                    false);
            g.drawString(
                    f,
                    f.plainSubstrByWidth(row.value(), span - key - 14),
                    left + key,
                    y + 8,
                    WorkshopSkin.TEXT,
                    false);
        }
        if (report.rows().size() > visible(height))
            g.drawString(
                    f,
                    (offset + 1)
                            + "–"
                            + limit
                            + " / "
                            + report.rows().size()
                            + dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.render._probe_renderer.002"),
                    left,
                    height - 79,
                    WorkshopSkin.MUTED,
                    false);
        if (report.total() > 0) {
            g.fill(left, height - 66, left + span, height - 62, 0xff253037);
            int fill = (int) ((long) span * report.checked() / report.total());
            g.fill(left, height - 66, left + fill, height - 62, WorkshopSkin.TEAL);
        }
        var lines = f.split(Component.literal(report.note()), span);
        for (int i = 0; i < Math.min(2, lines.size()); i++)
            g.drawString(f, lines.get(i), left, height - 56 + i * 10, WorkshopSkin.GOLD, false);
    }
}
