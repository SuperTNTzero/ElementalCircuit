package dev.elementalcircuits.client.screen;

import dev.elementalcircuits.adapter.codec.BlueprintTextCode;
import dev.elementalcircuits.client.book.BookAnimationClient;
import dev.elementalcircuits.client.book.BookRenderer;
import dev.elementalcircuits.client.editor.BookLayout;
import dev.elementalcircuits.client.render.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.book.NotebookInspection;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.network.*;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Physical library navigation and draft import. The server owns saved pages. */
public final class NotebookScreen extends Screen {
    private final CircuitScreen parent;
    private NotebookInspection view;
    private BookLayout layout;
    private EditBox name;
    private String error = "";
    private int offset, deleteId;

    public NotebookScreen(CircuitScreen parent, NotebookInspection v) {
        super(
                dev.elementalcircuits.client.text.UiText.component(
                        "screen.elementalcircuits.blueprint_notebook"));
        this.parent = parent;
        view = v;
        for (int i = 0; i < v.pages().size(); i++)
            if (v.pages().get(i).id() == v.selected()) offset = i;
    }

    public void accept(NotebookInspection v) {
        if (!view.identity().equals(v.identity())) return;
        boolean selection = view.selected() != v.selected();
        if (selection)
            BookAnimationClient.page(v.identity(), Integer.compare(v.selected(), view.selected()));
        view = v;
        deleteId = 0;
        error = "";
        if (selection)
            offset =
                    Math.max(
                            0,
                            v.pages()
                                            .indexOf(
                                                    v.pages().stream()
                                                            .filter(p -> p.id() == v.selected())
                                                            .findFirst()
                                                            .orElse(null))
                                    - Math.max(1, (height - 132) / 23)
                                    + 1);
        rebuildWidgets();
    }

    public void acceptEditor(CircuitNetwork.Editor p) {
        if (parent != null) parent.accept(p);
    }

    void request(int action, int id, String title, String text) {
        BookNetwork.send(new BookNetwork.NotebookRequest(view.identity(), action, id, title, text));
    }

    private void button(int x, int y, int w, String text, Runnable action) {
        var b =
                new CircuitButton(
                        x,
                        y,
                        w,
                        21,
                        text,
                        false,
                        null,
                        false,
                        () -> {
                            try {
                                error = "";
                                action.run();
                            } catch (RuntimeException e) {
                                error =
                                        e.getMessage() == null
                                                ? dev.elementalcircuits.client.text.UiText.string(
                                                        "text.elementalcircuits.client.screen._notebook_screen.001")
                                                : e.getMessage();
                            }
                        });
        b.setTooltip(Tooltip.create(Component.literal(text)));
        addRenderableWidget(b);
    }

    @Override
    protected void init() {
        layout = BookLayout.of(width, height);
        int left = layout.left(), span = layout.span(), visible = Math.max(1, (height - 132) / 23);
        offset = Math.max(0, Math.min(offset, Math.max(0, view.pages().size() - visible)));
        for (int i = offset; i < Math.min(view.pages().size(), offset + visible); i++) {
            var page = view.pages().get(i);
            addRenderableWidget(
                    new CircuitButton(
                            left,
                            36 + (i - offset) * 23,
                            layout.list(),
                            21,
                            page.title(),
                            page.id() == view.selected(),
                            null,
                            false,
                            () -> request(0, page.id(), "", "")));
        }
        button(
                width - 63,
                4,
                55,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._notebook_screen.002"),
                this::onClose);
        int y = height - 89;
        name =
                addRenderableWidget(
                        new EditBox(
                                font,
                                left,
                                y,
                                span - 60,
                                19,
                                Component.literal(
                                        dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._notebook_screen.003"))));
        name.setMaxLength(32);
        name.setValue(
                view.pages().stream()
                        .filter(p -> p.id() == view.selected())
                        .map(NotebookInspection.Page::title)
                        .findFirst()
                        .orElse(
                                dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._notebook_screen.004")));
        button(
                left + span - 56,
                y,
                56,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._notebook_screen.005"),
                () -> {
                    requirePage();
                    request(2, view.selected(), name.getValue(), "");
                });
        int half = (span - 4) / 2;
        button(
                left,
                y + 24,
                half,
                parent == null
                        ? dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._notebook_screen.006")
                        : dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._notebook_screen.007"),
                () -> {
                    if (parent == null)
                        minecraft.setScreen(new NotebookImportScreen(this, name.getValue()));
                    else
                        request(
                                1,
                                0,
                                name.getValue(),
                                BlueprintTextCode.encode(parent.draftForExport()));
                });
        button(
                left + half + 4,
                y + 24,
                span - half - 4,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._notebook_screen.008"),
                () -> {
                    requirePage();
                    minecraft.keyboardHandler.setClipboard(
                            BlueprintTextCode.encode(view.blueprint()));
                    error =
                            dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.screen._notebook_screen.009");
                });
        button(
                left,
                y + 48,
                half,
                parent == null
                        ? dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._notebook_screen.002")
                        : dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._notebook_screen.010"),
                () -> {
                    if (parent == null) onClose();
                    else {
                        requirePage();
                        var source = view.blueprint();
                        var target =
                                new Blueprint(
                                        source.version(),
                                        parent.draftForExport().rules(),
                                        source.runes(),
                                        source.routes(),
                                        source.continuous());
                        parent.acceptImported(target);
                        onClose();
                    }
                });
        button(
                left + half + 4,
                y + 48,
                span - half - 4,
                deleteId == view.selected() && deleteId != 0
                        ? dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._notebook_screen.011")
                        : dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._notebook_screen.012"),
                () -> {
                    requirePage();
                    if (deleteId == view.selected()) {
                        request(3, view.selected(), "", "");
                        deleteId = 0;
                    } else {
                        deleteId = view.selected();
                        rebuildWidgets();
                        error =
                                dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._notebook_screen.013");
                    }
                });
    }

    private void requirePage() {
        if (view.blueprint() == null)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.screen._notebook_screen.014"));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double d) {
        offset += d > 0 ? -1 : 1;
        rebuildWidgets();
        return true;
    }

    @Override
    public void tick() {
        if (parent != null) parent.tick();
    }

    // Screen.render invokes the vanilla background in 1.21; our renderer already painted it.
    @Override
    public void renderBackground(GuiGraphics g, int x, int y, float partial) {}

    @Override
    public void render(GuiGraphics g, int x, int y, float p) {
        g.drawManaged(
                () -> {
                    BookRenderer.bookFrame(
                            g,
                            font,
                            width,
                            height,
                            dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._notebook_screen.015")
                                    + view.pages().size()
                                    + " / 16",
                            error.isEmpty() ? view.message() : error,
                            true);
                    BookRenderer.preview(g, font, layout, view.blueprint());
                });
        super.render(g, x, y, p);
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
