package dev.elementalcircuits.client.screen;

import dev.elementalcircuits.application.observation.AssemblyInspection;
import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.client.render.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.network.CircuitNetwork;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/** Widget/network adapter for physical slots and explicit material installation. */
public final class AssemblyScreen extends Screen {
    private record Row(
            String text,
            boolean selected,
            EditorIcon icon,
            Runnable action,
            AssemblyInspection.Slot slot) {
        Row(String text, boolean selected, EditorIcon icon, Runnable action) {
            this(text, selected, icon, action, null);
        }
    }

    private final CircuitScreen parent;
    private final AssemblyPageModel model;
    private int left, span, total;
    private String hint, error = "";

    public AssemblyScreen(CircuitScreen parent, int page) {
        super(
                dev.elementalcircuits.client.text.UiText.component(
                        "screen.elementalcircuits.assembly"));
        this.parent = parent;
        model = new AssemblyPageModel(page);
    }

    private CircuitButton button(
            int x, int y, int w, String text, boolean selected, EditorIcon icon, Runnable action) {
        CircuitButton b =
                new CircuitButton(
                                x,
                                y,
                                w,
                                22,
                                font.plainSubstrByWidth(
                                        text, Math.max(5, w - (icon == null ? 11 : 32))),
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
                                                        ? dev.elementalcircuits.client.text.UiText
                                                                .string(
                                                                        "text.elementalcircuits.client.screen._assembly_screen.001")
                                                        : e.getMessage();
                                    }
                                })
                        .icon(icon);
        b.setTooltip(Tooltip.create(Component.literal(text)));
        addRenderableWidget(b);
        return b;
    }

    private void row(List<Row> rows, String text, EditorIcon icon, Runnable action) {
        rows.add(new Row(text, false, icon, action));
    }

    @Override
    protected void init() {
        left = Math.max(8, (width - 560) / 2);
        span = width - 2 * left;
        String[] tabs = {
            dev.elementalcircuits.client.text.UiText.string(
                    "text.elementalcircuits.client.screen._assembly_screen.002"),
            dev.elementalcircuits.client.text.UiText.string(
                    "text.elementalcircuits.client.screen._assembly_screen.003")
        };
        int[] pages = {0, 2};
        int tab = (span - 4) / 2;
        for (int i = 0; i < 2; i++) {
            final int page = pages[i];
            button(
                    left + i * (tab + 4),
                    31,
                    tab,
                    tabs[i],
                    model.page == page,
                    null,
                    () -> {
                        model.page = page;
                        model.offset = 0;
                        rebuildWidgets();
                    });
        }
        var data = parent.inspection();
        List<Row> rows = new ArrayList<>();
        if (model.page == 0) {
            hint =
                    dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.screen._assembly_screen.004")
                            + data.selected()
                            + " / "
                            + data.maxSlots();
            for (var s : data.slots())
                rows.add(
                        new Row(
                                dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._assembly_screen.004")
                                        + s.id(),
                                s.id() == data.selected(),
                                null,
                                () ->
                                        parent.managementRequest(
                                                parent.slot(), 8, Integer.toString(s.id())),
                                s));
            if (data.slots().size() < data.maxSlots()) {
                row(
                        rows,
                        (model.addExpanded ? "- " : "+ ")
                                + dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._assembly_screen.005"),
                        EditorIcon.action(EditorIcon.Action.PLATE),
                        () -> {
                            model.addExpanded = !model.addExpanded;
                            rebuildWidgets();
                        });
                if (model.addExpanded)
                    for (var m : Rules.Material.values())
                        row(
                                rows,
                                "    "
                                        + material(m)
                                        + dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._assembly_screen.006")
                                        + stock(data, m),
                                EditorIcon.action(EditorIcon.Action.PLATE),
                                () -> parent.managementRequest(parent.slot(), 9, m.name()));
            }
            if (data.fixed())
                row(
                        rows,
                        dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._assembly_screen.007")
                                + Fixed.display(data.charge())
                                + " / "
                                + Fixed.display(data.capacity()),
                        EditorIcon.rune(Rune.Kind.INPUT),
                        () -> parent.managementRequest(parent.slot(), 12, ""));
        } else if (model.page == 2) {
            hint =
                    dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.screen._assembly_screen.004")
                            + data.selected();
            var current =
                    data.slots().stream()
                            .filter(s -> s.id() == data.selected())
                            .findFirst()
                            .orElseThrow();
            if (current.boardRemaining() < current.boardMaximum())
                row(
                        rows,
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._assembly_screen.008"),
                        EditorIcon.action(EditorIcon.Action.PLATE),
                        () -> parent.managementRequest(parent.slot(), 20, ""));
            row(
                    rows,
                    (model.plateExpanded ? "- " : "+ ")
                            + material(parent.draftForExport().rules().material())
                            + dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.screen._assembly_screen.003"),
                    EditorIcon.action(EditorIcon.Action.PLATE),
                    () -> {
                        model.plateExpanded = !model.plateExpanded;
                        rebuildWidgets();
                    });
            if (model.plateExpanded)
                for (var m : Rules.Material.values()) {
                    boolean installed = parent.draftForExport().rules().material() == m;
                    rows.add(
                            new Row(
                                    "    "
                                            + material(m)
                                            + (installed
                                                    ? dev.elementalcircuits.client.text.UiText
                                                            .string(
                                                                    "text.elementalcircuits.client.screen._assembly_screen.009")
                                                    : dev.elementalcircuits.client.text.UiText
                                                                    .string(
                                                                            "text.elementalcircuits.client.screen._assembly_screen.006")
                                                            + stock(data, m)),
                                    installed,
                                    EditorIcon.action(EditorIcon.Action.PLATE),
                                    () -> {
                                        if (!installed) parent.installMaterial(m);
                                    }));
                }
        } else {
            hint = "";
        }
        total = rows.size();
        model.offset = Math.max(0, Math.min(model.offset, Math.max(0, total - 1)));
        int y = 76;
        for (int i = model.offset; i < total; i++) {
            var entry = rows.get(i);
            int h = entry.slot() == null ? 24 : 42;
            if (y + h > height - 67) break;
            if (entry.slot() != null)
                addRenderableWidget(
                        new CircuitSlotButton(
                                left,
                                y,
                                span - 24,
                                entry.slot(),
                                entry.selected(),
                                entry.action()));
            else {
                boolean child = entry.text().startsWith("    ");
                var b =
                        button(
                                left + (child ? 32 : 12),
                                y,
                                span - (child ? 76 : 36),
                                entry.text().stripLeading(),
                                entry.selected(),
                                entry.icon(),
                                entry.action());
                if (child) b.setHeight(18);
            }
            y += h;
        }
        if (model.offset > 0
                || y < 76 + rows.stream().mapToInt(r -> r.slot() == null ? 24 : 42).sum()) {
            button(
                    left + span - 20,
                    76,
                    20,
                    "↑",
                    false,
                    null,
                    () -> {
                        model.offset--;
                        rebuildWidgets();
                    });
            button(
                    left + span - 20,
                    height - 90,
                    20,
                    "↓",
                    false,
                    null,
                    () -> {
                        model.offset++;
                        rebuildWidgets();
                    });
        }
        button(
                left + span - 58,
                height - 31,
                58,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._assembly_screen.010"),
                false,
                null,
                this::onClose);
    }

    public void accept(CircuitNetwork.Editor packet) {
        parent.accept(packet);
        if (minecraft != null) rebuildWidgets();
    }

    @Override
    public void tick() {
        parent.tick();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double delta) {
        model.offset += delta > 0 ? -1 : 1;
        rebuildWidgets();
        return true;
    }

    // Screen.render invokes the vanilla background in 1.21; our renderer already painted it.
    @Override
    public void renderBackground(GuiGraphics g, int x, int y, float partial) {}

    @Override
    public void render(GuiGraphics g, int x, int y, float partial) {
        g.drawManaged(
                () ->
                        AssemblyRenderer.paint(
                                g,
                                font,
                                width,
                                height,
                                left,
                                span,
                                hint,
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

    private static String material(Rules.Material m) {
        return switch (m) {
            case WOOD ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.screen._assembly_screen.011");
            case BRASS ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.screen._assembly_screen.012");
            case CRYSTAL ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.screen._assembly_screen.013");
        };
    }

    private static int stock(AssemblyInspection d, Rules.Material m) {
        return d.stock().stream()
                .filter(s -> s.material() == m)
                .mapToInt(AssemblyInspection.Stock::count)
                .sum();
    }
}
