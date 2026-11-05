package dev.elementalcircuits.client.screen;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.application.observation.AssemblyInspection;
import dev.elementalcircuits.client.editor.TriggerDraft;
import dev.elementalcircuits.client.render.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.TriggerBinding;
import dev.elementalcircuits.network.CircuitNetwork;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/** One physical plate's layer graph and explicit trigger editing; no cross-slot authoring. */
public final class LayerScreen extends Screen {
    private final CircuitScreen parent;
    private int page, offset, source = -1;
    private boolean enabled, expanded;
    private TriggerDraft draft;
    private String error = "";

    private record Row(String label, boolean selected, Runnable action) {}

    public LayerScreen(CircuitScreen parent) {
        super(
                dev.elementalcircuits.client.text.UiText.component(
                        "screen.elementalcircuits.layers"));
        this.parent = parent;
    }

    private CircuitButton button(
            int x, int y, int w, String text, boolean selected, Runnable action) {
        var b =
                new CircuitButton(
                        x,
                        y,
                        w,
                        22,
                        text,
                        selected,
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
                                                        "text.elementalcircuits.client.screen._layer_screen.001")
                                                : e.getMessage();
                            }
                        });
        b.setTooltip(Tooltip.create(Component.literal(text)));
        addRenderableWidget(b);
        return b;
    }

    private static String name(int id) {
        return id == 1
                ? dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._layer_screen.002")
                : dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._layer_screen.003")
                        + id;
    }

    private static String condition(TriggerBinding.When when) {
        return Component.translatable(
                        "trigger.elementalcircuits." + when.name().toLowerCase(Locale.ROOT))
                .getString();
    }

    @Override
    protected void init() {
        var data = parent.inspection();
        int left = Math.max(8, (width - 560) / 2), span = width - left * 2, half = (span - 4) / 2;
        button(
                left,
                31,
                half,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._layer_screen.004"),
                page == 0,
                () -> {
                    page = 0;
                    offset = 0;
                    rebuildWidgets();
                });
        button(
                left + half + 4,
                31,
                half,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._layer_screen.005"),
                page == 1,
                () -> {
                    page = 1;
                    offset = 0;
                    rebuildWidgets();
                });
        if (source != parent.layer()) {
            source = parent.layer();
            var saved =
                    data.layers().stream()
                            .filter(l -> l.id() == source)
                            .map(AssemblyInspection.Layer::trigger)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(null);
            enabled = saved != null;
            expanded = saved == null;
            int target =
                    data.layers().stream()
                            .filter(l -> l.id() != source)
                            .mapToInt(AssemblyInspection.Layer::id)
                            .findFirst()
                            .orElse(source);
            draft = saved == null ? TriggerDraft.empty(source, target) : TriggerDraft.from(saved);
        }
        List<Row> rows = new ArrayList<>();
        if (page == 0) {
            if (!data.triggers().isEmpty())
                rows.add(
                        new Row(
                                dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._layer_screen.006"),
                                false,
                                () -> {}));
            for (var layer : data.layers()) {
                var t = layer.trigger();
                String label =
                        name(layer.id())
                                + " · "
                                + layer.runes()
                                + dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._layer_screen.007")
                                + (t == null
                                        ? ""
                                        : " → " + name(t.target()) + " · " + condition(t.when()));
                rows.add(
                        new Row(
                                label,
                                layer.id() == parent.layer(),
                                () -> parent.layerRequest(layer.id(), 17, "")));
            }
            if (data.layers().size() - 1 < data.depthLimit())
                rows.add(
                        new Row(
                                dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._layer_screen.008"),
                                false,
                                () -> parent.layerRequest(parent.layer(), 16, "")));
            rows.add(
                    new Row(
                            dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._layer_screen.009")
                                    + name(parent.layer()),
                            false,
                            this::onClose));
            if (parent.layer() != 1)
                rows.add(
                        new Row(
                                dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._layer_screen.010")
                                        + name(parent.layer()),
                                false,
                                () -> parent.layerRequest(parent.layer(), 18, "")));
        } else {
            rows.add(
                    new Row(
                            dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._layer_screen.011")
                                    + name(parent.layer()),
                            true,
                            () -> {}));
            rows.add(
                    new Row(
                            dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._layer_screen.012")
                                    + (enabled
                                            ? dev.elementalcircuits.client.text.UiText.string(
                                                    "text.elementalcircuits.client.screen._layer_screen.013")
                                            : dev.elementalcircuits.client.text.UiText.string(
                                                    "text.elementalcircuits.client.screen._layer_screen.014")),
                            enabled,
                            () -> {
                                enabled = !enabled;
                                rebuildWidgets();
                            }));
            if (enabled) {
                var t = draft;
                rows.add(
                        new Row(
                                dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._layer_screen.015")
                                        + name(t.target()),
                                false,
                                () -> {
                                    var ids =
                                            data.layers().stream()
                                                    .map(AssemblyInspection.Layer::id)
                                                    .toList();
                                    draft =
                                            t.target(
                                                    ids.get(
                                                            Math.floorMod(
                                                                    ids.indexOf(t.target()) + 1,
                                                                    ids.size())));
                                    rebuildWidgets();
                                }));
                rows.add(
                        new Row(
                                (expanded ? "- " : "+ ")
                                        + dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._layer_screen.016")
                                        + (t.ready()
                                                ? condition(t.when())
                                                : dev.elementalcircuits.client.text.UiText.string(
                                                        "text.elementalcircuits.client.screen._layer_screen.017")),
                                false,
                                () -> {
                                    expanded = !expanded;
                                    rebuildWidgets();
                                }));
                if (expanded)
                    for (var when : TriggerBinding.When.values())
                        rows.add(
                                new Row(
                                        "    " + condition(when),
                                        when == t.when(),
                                        () -> {
                                            draft = t.condition(when);
                                            expanded = false;
                                            rebuildWidgets();
                                        }));
                if (t.ready() && t.when().usesDelay())
                    rows.add(
                            new Row(
                                    dev.elementalcircuits.client.text.UiText.string(
                                                    "text.elementalcircuits.client.screen._layer_screen.018")
                                            + t.delay()
                                            + dev.elementalcircuits.client.text.UiText.string(
                                                    "text.elementalcircuits.client.screen._layer_screen.019"),
                                    false,
                                    () ->
                                            minecraft.setScreen(
                                                    new IntegerEntryScreen(
                                                            this,
                                                            dev.elementalcircuits.client.text.UiText
                                                                    .string(
                                                                            "text.elementalcircuits.client.screen._layer_screen.020"),
                                                            t.delay(),
                                                            1,
                                                            72000,
                                                            n -> draft = t.delay(n)))));
                rows.add(
                        new Row(
                                dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._layer_screen.021")
                                        + t.percent()
                                        + "%",
                                false,
                                () ->
                                        minecraft.setScreen(
                                                new IntegerEntryScreen(
                                                        this,
                                                        dev.elementalcircuits.client.text.UiText
                                                                .string(
                                                                        "text.elementalcircuits.client.screen._layer_screen.022"),
                                                        t.percent(),
                                                        5,
                                                        95,
                                                        n -> draft = t.percent(n)))));
            }
            button(
                                    left,
                                    height - 31,
                                    Math.min(154, span - 64),
                                    dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._layer_screen.023"),
                                    false,
                                    () ->
                                            parent.layerRequest(
                                                    parent.layer(),
                                                    19,
                                                    enabled
                                                            ? JsonCodecs.triggerBinding(
                                                                    draft.binding())
                                                            : "off"))
                            .active =
                    !enabled || draft.ready();
        }
        int visible = Math.max(1, (height - 143) / 24);
        offset = Math.max(0, Math.min(offset, Math.max(0, rows.size() - visible)));
        for (int i = 0; i < visible && i + offset < rows.size(); i++) {
            var row = rows.get(i + offset);
            button(left, 76 + i * 24, span - 24, row.label(), row.selected(), row.action());
        }
        if (rows.size() > visible) {
            button(
                    left + span - 20,
                    76,
                    20,
                    "↑",
                    false,
                    () -> {
                        offset--;
                        rebuildWidgets();
                    });
            button(
                    left + span - 20,
                    height - 90,
                    20,
                    "↓",
                    false,
                    () -> {
                        offset++;
                        rebuildWidgets();
                    });
        }
        button(
                left + span - 58,
                height - 31,
                58,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._layer_screen.024"),
                false,
                this::onClose);
    }

    public void accept(CircuitNetwork.Editor packet) {
        parent.accept(packet);
        if (packet.message()
                .equals(
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._layer_screen.025")))
            source = -1;
        if (minecraft != null) rebuildWidgets();
    }

    @Override
    public void tick() {
        parent.tick();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double delta) {
        if (delta != 0) {
            offset += delta > 0 ? -1 : 1;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int x, int y, float partial) {}

    @Override
    public void render(GuiGraphics g, int x, int y, float partial) {
        int left = Math.max(8, (width - 560) / 2), span = width - left * 2;
        g.drawManaged(
                () ->
                        AssemblyRenderer.paint(
                                g,
                                font,
                                width,
                                height,
                                left,
                                span,
                                dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._layer_screen.026"),
                                dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._layer_screen.027")
                                        + parent.slot()
                                        + " · "
                                        + name(parent.layer())
                                        + dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._layer_screen.028")
                                        + parent.inspection().depthLimit(),
                                error.isEmpty() ? parent.message() : error));
        super.render(g, x, y, partial);
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
