package dev.elementalcircuits.client.screen;

import dev.elementalcircuits.adapter.codec.BlueprintTextCode;
import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.client.render.*;
import dev.elementalcircuits.network.CircuitNetwork;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Input/clipboard adapter. Import validates fully before changing the parent draft. */
public final class BlueprintScreen extends Screen {
    private final CircuitScreen parent;
    private final BlueprintTransferModel model;
    private BlueprintTransferLayout layout;
    private MultiLineEditBox code;

    public BlueprintScreen(CircuitScreen parent, BlueprintTransferModel model) {
        super(
                dev.elementalcircuits.client.text.UiText.component(
                        "screen.elementalcircuits.blueprint"));
        this.parent = parent;
        this.model = model;
    }

    private void button(
            int x,
            int y,
            int w,
            String title,
            boolean selected,
            EditorIcon.Action icon,
            Runnable action) {
        addRenderableWidget(
                new CircuitButton(
                                x,
                                y,
                                w,
                                22,
                                title,
                                selected,
                                null,
                                false,
                                () -> {
                                    try {
                                        action.run();
                                    } catch (RuntimeException e) {
                                        model.message =
                                                e.getMessage() == null
                                                        ? dev.elementalcircuits.client.text.UiText
                                                                .string(
                                                                        "text.elementalcircuits.client.screen._blueprint_screen.001")
                                                        : e.getMessage();
                                    }
                                })
                        .icon(EditorIcon.action(icon)));
    }

    private void mode(boolean export) {
        model.exporting = export;
        model.message =
                export
                        ? dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._blueprint_screen.002")
                        : dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._blueprint_screen.003");
        rebuildWidgets();
    }

    @Override
    protected void init() {
        layout = BlueprintTransferLayout.of(width, height);
        int x = layout.left(), w = layout.boxWidth(), tab = (w - 4) / 2;
        button(
                x,
                32,
                tab,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._blueprint_screen.004"),
                !model.exporting,
                EditorIcon.Action.IMPORT,
                () -> mode(false));
        button(
                x + tab + 4,
                32,
                w - tab - 4,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._blueprint_screen.005"),
                model.exporting,
                EditorIcon.Action.EXPORT,
                () -> mode(true));
        button(
                x + w - 56,
                5,
                56,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._blueprint_screen.006"),
                false,
                EditorIcon.Action.CLOSE,
                this::onClose);
        code =
                addRenderableWidget(
                        new MultiLineEditBox(
                                font,
                                x,
                                layout.top(),
                                w,
                                layout.boxHeight(),
                                Component.literal(
                                        model.exporting
                                                ? dev.elementalcircuits.client.text.UiText.string(
                                                        "text.elementalcircuits.client.screen._blueprint_screen.007")
                                                : dev.elementalcircuits.client.text.UiText.string(
                                                        "text.elementalcircuits.client.screen._blueprint_screen.008")),
                                Component.literal(
                                        dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._blueprint_screen.009"))));
        code.setCharacterLimit(BlueprintTextCode.LIMIT);
        code.setValue(model.text());
        code.setValueListener(model::text);
        int y = layout.actions();
        if (model.exporting)
            button(
                    x,
                    y,
                    w,
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.screen._blueprint_screen.010"),
                    false,
                    EditorIcon.Action.EXPORT,
                    () -> {
                        String text = model.export(parent.draftForExport());
                        code.setValue(text);
                        minecraft.keyboardHandler.setClipboard(text);
                        model.message =
                                dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._blueprint_screen.011");
                    });
        else {
            button(
                    x,
                    y,
                    tab,
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.screen._blueprint_screen.012"),
                    false,
                    EditorIcon.Action.IMPORT,
                    () -> {
                        String text = minecraft.keyboardHandler.getClipboard();
                        if (text.length() > BlueprintTextCode.LIMIT)
                            throw new IllegalArgumentException(
                                    dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._blueprint_screen.013"));
                        code.setValue(text);
                        model.message =
                                dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._blueprint_screen.014");
                    });
            button(
                    x + tab + 4,
                    y,
                    w - tab - 4,
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.screen._blueprint_screen.015"),
                    false,
                    EditorIcon.Action.APPLY,
                    () -> {
                        var blueprint = model.imported(parent.draftForExport().rules());
                        parent.acceptImported(blueprint);
                        onClose();
                    });
        }
    }

    public void accept(CircuitNetwork.Editor packet) {
        parent.accept(packet);
    }

    @Override
    public void tick() {
        super.tick();
        parent.tick();
    }

    // Screen.render invokes the vanilla background in 1.21; our renderer already painted it.
    @Override
    public void renderBackground(GuiGraphics g, int x, int y, float partial) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        BlueprintRenderer.paint(g, font, layout, model.exporting, model.message);
        super.render(g, mouseX, mouseY, partial);
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
