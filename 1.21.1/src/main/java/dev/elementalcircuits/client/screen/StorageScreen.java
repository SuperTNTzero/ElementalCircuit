package dev.elementalcircuits.client.screen;

import dev.elementalcircuits.client.render.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.RuneParameterValue;
import dev.elementalcircuits.domain.mana.StorageSettings;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Widget adapter only; validation is shared by blueprints, network admission and runtime. */
public final class StorageScreen extends Screen {
    private final Screen parent;
    private final Rune rune;
    private final Consumer<Rune> apply;
    private EditBox capacity, threshold, release;
    private CircuitButton mode;
    private boolean signal;
    private String message = "";

    public StorageScreen(Screen parent, Rune rune, Consumer<Rune> apply) {
        super(
                dev.elementalcircuits.client.text.UiText.component(
                        "screen.elementalcircuits.mana_storage"));
        this.parent = parent;
        this.rune = rune;
        this.apply = apply;
        signal = rune.signalControl();
    }

    private EditBox field(int x, int y, int w, long value) {
        var box =
                addRenderableWidget(
                        new EditBox(
                                font,
                                x,
                                y,
                                w,
                                20,
                                dev.elementalcircuits.client.text.UiText.component(
                                        "ui.elementalcircuits.mana_amount")));
        box.setMaxLength(256);
        box.setValue(
                java.math.BigDecimal.valueOf(value)
                        .divide(java.math.BigDecimal.valueOf(Fixed.ONE))
                        .stripTrailingZeros()
                        .toPlainString());
        return box;
    }

    private CircuitButton button(int x, int y, int w, String title, Runnable action) {
        return addRenderableWidget(
                new CircuitButton(
                                x,
                                y,
                                w,
                                22,
                                title,
                                false,
                                null,
                                false,
                                () -> {
                                    try {
                                        action.run();
                                    } catch (RuntimeException e) {
                                        message =
                                                e.getMessage() == null
                                                        ? dev.elementalcircuits.client.text.UiText
                                                                .string(
                                                                        "text.elementalcircuits.client.screen._storage_screen.001")
                                                        : e.getMessage();
                                    }
                                })
                        .icon(EditorIcon.action(EditorIcon.Action.APPLY)));
    }

    @Override
    protected void init() {
        String[] draft =
                capacity == null
                        ? null
                        : new String[] {
                            capacity.getValue(), threshold.getValue(), release.getValue()
                        };
        int x = width / 2 - 110, y = Math.max(45, height / 2 - 110);
        var s = rune.storage();
        capacity = field(x, y + 20, 220, s.capacity());
        threshold = field(x, y + 64, 220, s.threshold());
        release = field(x, y + 108, 220, s.release());
        if (draft != null) {
            capacity.setValue(draft[0]);
            threshold.setValue(draft[1]);
            release.setValue(draft[2]);
        }
        mode =
                button(
                        x,
                        y + 138,
                        220,
                        signal
                                ? dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._storage_screen.002")
                                : dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._storage_screen.003"),
                        () -> {
                            signal = !signal;
                            mode.setMessage(
                                    Component.literal(
                                            signal
                                                    ? dev.elementalcircuits.client.text.UiText
                                                            .string(
                                                                    "text.elementalcircuits.client.screen._storage_screen.002")
                                                    : dev.elementalcircuits.client.text.UiText
                                                            .string(
                                                                    "text.elementalcircuits.client.screen._storage_screen.003")));
                        });
        button(
                x,
                y + 166,
                106,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._storage_screen.004"),
                () -> {
                    var settings =
                            new StorageSettings(
                                    amount(capacity), amount(threshold), amount(release));
                    if (settings.equals(StorageSettings.NONE))
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._storage_screen.005"));
                    apply.accept(
                            new Rune(
                                    rune.id(),
                                    rune.kind(),
                                    rune.cell(),
                                    rune.element(),
                                    rune.value(),
                                    true,
                                    signal,
                                    rune.weights(),
                                    settings));
                    onClose();
                });
        button(
                x + 114,
                y + 166,
                106,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._storage_screen.006"),
                this::onClose);
    }

    private long amount(EditBox box) {
        return RuneParameterValue.parse(Rune.Kind.AMOUNT, box.getValue());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics g, int x, int y, float p) {}

    @Override
    public void render(GuiGraphics g, int x, int y, float p) {
        WorkshopSkin.background(g, width, height);
        int left = width / 2 - 110, top = Math.max(45, height / 2 - 110);
        g.drawCenteredString(font, title, width / 2, top - 22, WorkshopSkin.GOLD);
        g.drawString(
                font,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._storage_screen.007"),
                left,
                top + 7,
                WorkshopSkin.TEXT,
                false);
        g.drawString(
                font,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._storage_screen.008"),
                left,
                top + 51,
                WorkshopSkin.TEXT,
                false);
        g.drawString(
                font,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._storage_screen.009"),
                left,
                top + 95,
                WorkshopSkin.TEXT,
                false);
        super.render(g, x, y, p);
        g.drawCenteredString(
                font,
                font.plainSubstrByWidth(message, width - 24),
                width / 2,
                top + 198,
                WorkshopSkin.MUTED);
    }
}
