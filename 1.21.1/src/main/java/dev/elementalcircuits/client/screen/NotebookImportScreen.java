package dev.elementalcircuits.client.screen;

import dev.elementalcircuits.adapter.codec.BlueprintTextCode;
import dev.elementalcircuits.client.book.BookRenderer;
import dev.elementalcircuits.client.render.*;
import dev.elementalcircuits.domain.book.NotebookInspection;
import dev.elementalcircuits.network.CircuitNetwork;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class NotebookImportScreen extends Screen {
    private final NotebookScreen parent;
    private final String pageName;
    private MultiLineEditBox code;
    private String text = "",
            message =
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.screen._notebook_import_screen.001");

    public NotebookImportScreen(NotebookScreen parent, String pageName) {
        super(
                dev.elementalcircuits.client.text.UiText.component(
                        "screen.elementalcircuits.notebook_import"));
        this.parent = parent;
        this.pageName = pageName;
    }

    @Override
    protected void init() {
        int left = Math.max(8, (width - 580) / 2), span = width - left * 2;
        code =
                addRenderableWidget(
                        new MultiLineEditBox(
                                font,
                                left,
                                36,
                                span,
                                height - 100,
                                Component.literal(
                                        dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._notebook_import_screen.002")),
                                Component.literal(
                                        dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._notebook_import_screen.003"))));
        code.setCharacterLimit(BlueprintTextCode.LIMIT);
        code.setValue(text);
        code.setValueListener(v -> text = v);
        addRenderableWidget(
                new CircuitButton(
                        left,
                        height - 55,
                        span / 2 - 2,
                        22,
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._notebook_import_screen.004"),
                        false,
                        null,
                        false,
                        () -> {
                            try {
                                BlueprintTextCode.decode(code.getValue());
                                parent.request(1, 0, pageName, code.getValue());
                                message =
                                        dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._notebook_import_screen.005");
                            } catch (RuntimeException e) {
                                message = e.getMessage();
                            }
                        }));
        addRenderableWidget(
                new CircuitButton(
                        left + span / 2 + 2,
                        height - 55,
                        span / 2 - 2,
                        22,
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._notebook_import_screen.006"),
                        false,
                        null,
                        false,
                        this::onClose));
    }

    public void acceptEditor(CircuitNetwork.Editor p) {
        parent.acceptEditor(p);
    }

    public void accept(NotebookInspection v) {
        parent.accept(v);
        minecraft.setScreen(parent);
    }

    @Override
    public void tick() {
        parent.tick();
    }

    // Screen.render invokes the vanilla background in 1.21; our renderer already painted it.
    @Override
    public void renderBackground(GuiGraphics g, int x, int y, float partial) {}

    @Override
    public void render(GuiGraphics g, int x, int y, float p) {
        g.drawManaged(
                () ->
                        BookRenderer.frame(
                                g,
                                font,
                                width,
                                height,
                                dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._notebook_import_screen.007"),
                                message));
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
