package dev.elementalcircuits.client.book;

import dev.elementalcircuits.adapter.resources.CooldownResources;
import dev.elementalcircuits.client.editor.BookLayout;
import dev.elementalcircuits.client.editor.WorkshopLayout;
import dev.elementalcircuits.client.render.EditorRenderer;
import dev.elementalcircuits.client.render.WorkshopSkin;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.book.WorkshopInspection;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.equipment.WandParts;

import net.minecraft.client.gui.*;

/** Book and workbench painting only; never modifies a draft or running circuit. */
public final class BookRenderer {
    private BookRenderer() {}

    public static void frame(
            GuiGraphics g, Font f, int width, int height, String title, String message) {
        WorkshopSkin.background(g, width, height);
        WorkshopSkin.title(g, f, f.plainSubstrByWidth(title, width - 95), 10, 10);
        g.drawString(
                f,
                f.plainSubstrByWidth(message == null ? "" : message, width - 20),
                10,
                height - 14,
                EditorRenderer.BLUE,
                false);
    }

    public static void bookFrame(
            GuiGraphics g,
            Font f,
            int width,
            int height,
            String title,
            String message,
            boolean notebook) {
        WorkshopSkin.background(g, width, height);
        WorkshopSkin.title(g, f, f.plainSubstrByWidth(title, width - 145), 10, 10);
        BookItemRenderer.preview(g, width - 108, 2, 22, notebook);
        g.drawString(
                f,
                f.plainSubstrByWidth(message == null ? "" : message, width - 20),
                10,
                height - 14,
                EditorRenderer.BLUE,
                false);
    }

    public static void preview(GuiGraphics g, Font f, BookLayout l, Blueprint b) {
        int x = l.previewX(), y = l.previewY(), w = l.previewWidth(), h = l.previewHeight();
        WorkshopSkin.panel(g, l.left(), y, l.list(), h, WorkshopSkin.BOOK);
        WorkshopSkin.panel(g, x, y, w, h, WorkshopSkin.BOARD);
        g.enableScissor(x + 3, y + 3, x + w - 3, y + h - 3);
        WorkshopSkin.seal(g, x + w / 2.0, y + h / 2.0, Math.min(w, h) / 2.0 - 6);
        if (b == null) {
            String empty =
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.book._book_renderer.001");
            g.drawString(
                    f,
                    empty,
                    x + Math.max(8, (w - f.width(empty)) / 2),
                    y + h / 2,
                    EditorRenderer.TEXT,
                    false);
            g.disableScissor();
            return;
        }
        int r = b.rules().boardRadius();
        double s = l.size(r);
        for (int q = -r; q <= r; q++)
            for (int t = -r; t <= r; t++) {
                Hex cell = new Hex(q, t);
                if (cell.inside(r))
                    EditorRenderer.hexagon(g, l.x(cell, r), l.y(cell, r), s * .93, 0xff34423e);
            }
        for (var route : b.routes()) {
            var path = route.path();
            for (int i = 1; i < path.size(); i++)
                EditorRenderer.line(
                        g,
                        l.x(path.get(i - 1), r),
                        l.y(path.get(i - 1), r),
                        l.x(path.get(i), r),
                        l.y(path.get(i), r),
                        route.payload() == Route.Payload.MANA
                                ? EditorRenderer.GOLD
                                : EditorRenderer.BLUE);
        }
        for (var n : b.runes()) {
            double nx = l.x(n.cell(), r), ny = l.y(n.cell(), r);
            g.fill(
                    (int) (nx - s * .6),
                    (int) (ny - s * .6),
                    (int) (nx + s * .6 + 1),
                    (int) (ny + s * .6 + 1),
                    0xff181f21);
            EditorRenderer.hexagon(g, nx, ny, s * .8, 0xff92957c);
            EditorRenderer.rune(
                    g,
                    n,
                    nx,
                    ny,
                    s * .54,
                    n.kind() == Rune.Kind.CONVERT
                            ? 0xff000000 | n.element().color
                            : EditorRenderer.TEXT);
        }
        g.disableScissor();
    }

    public static void workshop(
            GuiGraphics g,
            Font f,
            int width,
            int height,
            int left,
            int span,
            WorkshopInspection v,
            WandParts defs,
            WandParts.Assembly preview) {
        frame(
                g,
                f,
                width,
                height,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.book._book_renderer.002"),
                v.message());
        var l = WorkshopLayout.of(width, height);
        var stats = preview.stats(defs);
        String[] text = {
            dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.book._book_renderer.003")
                    + Fixed.display(stats.strength())
                    + dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.book._book_renderer.004")
                    + String.format(
                            java.util.Locale.ROOT,
                            dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.book._book_renderer.005"),
                            CooldownResources.rules().ticks(stats.speed()) / 20.0),
            dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.book._book_renderer.006")
                    + Math.max(0, stats.durability() - v.wear())
                    + " / "
                    + stats.durability()
                    + dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.book._book_renderer.007")
                    + stats.melee()
        };
        for (int i = 0; i < text.length; i++)
            g.drawString(
                    f,
                    f.plainSubstrByWidth(text[i], span),
                    left,
                    42 + i * 13,
                    i == 0 ? EditorRenderer.GOLD : EditorRenderer.TEXT,
                    false);
        int h = Math.max(28, l.bottom() - l.top());
        WorkshopSkin.panel(g, left, l.top(), l.previewWidth(), h, WorkshopSkin.BOARD);
        g.enableScissor(left + 3, l.top() + 3, left + l.previewWidth() - 3, l.bottom() - 3);
        WorkshopSkin.seal(
                g,
                left + l.previewWidth() / 2.0,
                l.top() + h / 2.0,
                Math.min(l.previewWidth(), h) / 2.0 - 5);
        EditorRenderer.wand(
                g,
                left + l.previewWidth() / 2,
                l.top() + Math.max(8, (h - l.wandSize()) / 2 - 8),
                l.wandSize(),
                preview,
                defs);
        String caption =
                preview.equals(v.assembly())
                        ? dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.book._book_renderer.008")
                        : dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.book._book_renderer.009");
        if (h > 50)
            g.drawString(
                    f,
                    caption,
                    left + Math.max(5, (l.previewWidth() - f.width(caption)) / 2),
                    l.bottom() - 17,
                    EditorRenderer.GOLD,
                    false);
        g.disableScissor();
    }
}
