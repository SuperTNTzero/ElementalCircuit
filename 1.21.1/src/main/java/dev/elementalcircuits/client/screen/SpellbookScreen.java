package dev.elementalcircuits.client.screen;

import dev.elementalcircuits.client.book.BookRenderer;
import dev.elementalcircuits.client.editor.BookLayout;
import dev.elementalcircuits.client.render.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.book.SpellbookInspection;
import dev.elementalcircuits.network.BookNetwork;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

public final class SpellbookScreen extends Screen {
    private SpellbookInspection view;
    private BookLayout layout;
    private int preview, refresh;

    public SpellbookScreen(SpellbookInspection v) {
        super(
                dev.elementalcircuits.client.text.UiText.component(
                        "screen.elementalcircuits.spellbook"));
        view = v;
        preview = v.selected();
    }

    public void accept(SpellbookInspection v) {
        if (!view.identity().equals(v.identity())) return;
        boolean changed = view.selected() != v.selected();
        view = v;
        if (changed || v.slots().stream().noneMatch(s -> s.id() == preview)) preview = v.selected();
        rebuildWidgets();
    }

    private void request(int action, int value) {
        BookNetwork.send(new BookNetwork.SpellRequest(view.identity(), action, value));
    }

    private void button(int x, int y, int w, String text, boolean selected, Runnable action) {
        addRenderableWidget(new CircuitButton(x, y, w, 22, text, selected, null, false, action));
    }

    @Override
    protected void init() {
        layout = BookLayout.of(width, height);
        for (int i = 0; i < view.slots().size(); i++) {
            var s = view.slots().get(i);
            button(
                    layout.left(),
                    36 + i * 24,
                    layout.list(),
                    dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.screen._spellbook_screen.001")
                            + s.id()
                            + (s.powered()
                                    ? dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._spellbook_screen.002")
                                    : dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._spellbook_screen.003")),
                    preview == s.id(),
                    () -> {
                        preview = s.id();
                        rebuildWidgets();
                    });
        }
        int left = layout.left(), span = layout.span(), half = (span - 4) / 2;
        button(
                left,
                height - 76,
                half,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._spellbook_screen.004"),
                false,
                () -> request(1, preview));
        button(
                left + half + 4,
                height - 76,
                span - half - 4,
                view.current().powered()
                        ? dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._spellbook_screen.005")
                        : dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._spellbook_screen.006"),
                false,
                () -> request(4, view.selected()));
        var hint =
                addRenderableWidget(
                        new CircuitButton(
                                left,
                                height - 51,
                                half,
                                22,
                                dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._spellbook_screen.007"),
                                false,
                                null,
                                false,
                                () -> {}));
        hint.active = false;
        button(
                left + half + 4,
                height - 51,
                span - half - 4,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._spellbook_screen.008"),
                false,
                this::onClose);
    }

    // Screen.render invokes the vanilla background in 1.21; our renderer already painted it.
    @Override
    public void renderBackground(GuiGraphics g, int x, int y, float partial) {}

    @Override
    public void render(GuiGraphics g, int x, int y, float p) {
        var slot =
                view.slots().stream()
                        .filter(s -> s.id() == preview)
                        .findFirst()
                        .orElse(view.current());
        g.drawManaged(
                () -> {
                    BookRenderer.bookFrame(
                            g,
                            font,
                            width,
                            height,
                            dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._spellbook_screen.009")
                                    + view.selected(),
                            view.message(),
                            false);
                    BookRenderer.preview(g, font, layout, slot.blueprint());
                    g.drawString(
                            font,
                            dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._spellbook_screen.010")
                                    + slot.id()
                                    + dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._spellbook_screen.011")
                                    + Fixed.display(slot.held()),
                            layout.previewX() + 5,
                            height - 89,
                            EditorRenderer.MUTED,
                            false);
                });
        super.render(g, x, y, p);
    }

    @Override
    public void tick() {
        if (minecraft.player != null && ++refresh >= 20) {
            refresh = 0;
            request(5, 0);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
