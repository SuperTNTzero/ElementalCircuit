package dev.elementalcircuits.client.screen;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.WandResources;
import dev.elementalcircuits.application.observation.AssemblyInspection;
import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.client.input.BookClient;
import dev.elementalcircuits.client.input.EquipmentClient;
import dev.elementalcircuits.client.render.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.CircuitComplexity;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.equipment.WandParts;
import dev.elementalcircuits.domain.logic.LogicObservation;
import dev.elementalcircuits.domain.logic.RuneLogic;
import dev.elementalcircuits.network.CircuitNetwork;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * Minecraft input/network/widget assembly. Rule edits live in EditorModel; painting in
 * EditorRenderer.
 */
public final class CircuitScreen extends Screen {
    private record BoardAddress(int slot, int layer) {}

    private final UUID identity;
    private EditorModel model;
    private int slot = 1, layer = 1, layerOffset;
    private boolean fixed, book, engraving, gear;
    private String complexityStatus = "";
    private AssemblyInspection inspection;
    private LogicObservation logic = new LogicObservation(1, List.of());
    private final Map<BoardAddress, EditorModel> models = new HashMap<>();
    private final Map<BoardAddress, Blueprint> serverBoards = new HashMap<>();

    public AssemblyInspection inspection() {
        return inspection;
    }

    public String message() {
        return model.message;
    }

    public int slot() {
        return slot;
    }

    public int layer() {
        return layer;
    }

    public void layers() {
        model.cancel();
        boardPress = false;
        minecraft.setScreen(new LayerScreen(this));
    }

    public void layerRequest(int target, int action, String payload) {
        UUID token = requests.begin(action);
        CircuitNetwork.request(identity, slot, action, payload, token, target);
        if (action != 5)
            model.message =
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.screen._circuit_screen.001");
    }

    public void manage(int page) {
        if (page == 3) {
            layers();
            return;
        }
        model.cancel();
        boardPress = false;
        minecraft.setScreen(new AssemblyScreen(this, page));
    }

    public void managementRequest(int slot, int action, String payload) {
        UUID token = requests.begin(action);
        CircuitNetwork.request(identity, slot, action, payload, token, layer);
        if (action != 5)
            model.message =
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.screen._circuit_screen.001");
    }

    public void installMaterial(Rules.Material material) {
        layerRequest(1, 4, material.name());
    }

    private EditorLayout layout;
    private final EditorViewport viewport = new EditorViewport();
    private boolean panning;

    private int toolbarY() {
        return width < 440 ? 65 : inspection.layers().isEmpty() ? 50 : 69;
    }

    private EditorLayout baseLayout() {
        return EditorLayout.of(
                width, height, model.draft.get().rules().boardRadius(), toolbarY() + 27);
    }

    private WandParts.Assembly assembly;
    private int wear;
    private boolean powered;
    private long held;
    private double pointerX = -1, pointerY = -1;
    private int contentHeight;
    private Blueprint wireBoard;
    private EditorLayout wireLayout;
    private Route.Payload wirePayload;
    private double wireX, wireY;
    private Route wireHover;

    private Route hoveredWire(double x, double y) {
        if (model.tool != EditorModel.Tool.ROUTE || model.drawing() || model.components)
            return null;
        if (wireBoard != model.draft.get()
                || !Objects.equals(wireLayout, layout)
                || wirePayload != model.payload
                || wireX != x
                || wireY != y) {
            wireBoard = model.draft.get();
            wireLayout = layout;
            wirePayload = model.payload;
            wireX = x;
            wireY = y;
            wireHover = RouteHitTest.at(wireBoard, layout, wirePayload, x, y);
        }
        return wireHover;
    }

    private Blueprint serverBlueprint;
    private final EditorRequests requests = new EditorRequests();
    private int refreshTicks;
    private boolean boardPress, focusParameter;
    private NativeEditorCursor nativeCursor;
    private double pressX, pressY;
    private final BlueprintTransferModel blueprints = new BlueprintTransferModel();

    public Blueprint draftForExport() {
        return model.draft.get();
    }

    public void acceptImported(Blueprint blueprint) {
        model.draft.set(blueprint);
        model.cancel();
        model.selected = -1;
        model.message =
                engraving
                        ? dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._circuit_screen.002")
                        : dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._circuit_screen.003");
    }

    public void openBlueprints() {
        model.cancel();
        boardPress = false;
        minecraft.setScreen(new BlueprintScreen(this, blueprints));
    }

    public CircuitScreen(CircuitNetwork.Editor packet) {
        super(
                dev.elementalcircuits.client.text.UiText.component(
                        "screen.elementalcircuits.circuit_editor"));
        identity = packet.identity();
        accept(packet);
    }

    public void accept(CircuitNetwork.Editor packet) {
        if (!identity.equals(packet.identity())) return;
        EditorRequests.Reply reply = requests.complete(packet.token());
        if (reply == EditorRequests.Reply.STALE) return;
        if (engraving && !packet.draft()) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal(packet.message()), true);
                minecraft.setScreen(null);
            }
            return;
        }
        engraving = packet.draft();
        Blueprint incoming = JsonCodecs.readBlueprint(packet.blueprint());
        int previousSlot = slot, previousLayer = layer;
        slot = packet.slot();
        layer = packet.layer();
        var address = new BoardAddress(slot, layer);
        fixed = packet.fixed();
        book = packet.book();
        gear = EquipmentClient.carrier(identity);
        model = models.get(address);
        serverBlueprint = serverBoards.get(address);
        boolean changed = !incoming.equals(serverBlueprint);
        if (model == null) {
            model = new EditorModel(incoming);
            models.put(address, model);
        } else if (!incoming.equals(serverBlueprint)) {
            model.draft.set(incoming);
            model.cancel();
            model.selected = -1;
        }
        serverBlueprint = incoming;
        serverBoards.put(address, incoming);
        if (!packet.management().isEmpty())
            inspection = JsonCodecs.readInspection(packet.management());
        else
            inspection =
                    new AssemblyInspection(
                            slot,
                            4,
                            false,
                            0,
                            0,
                            List.of(
                                    new AssemblyInspection.Slot(
                                            slot,
                                            incoming.rules().material(),
                                            packet.powered(),
                                            packet.held())),
                            List.of(),
                            List.of(),
                            List.of());
        if (previousSlot != slot || previousLayer != layer) {
            int index = -1;
            for (int i = 0; i < inspection.layers().size(); i++)
                if (inspection.layers().get(i).id() == layer) index = i;
            int count = Math.max(1, (width - 54) / 70);
            if (index >= 0 && (index < layerOffset || index >= layerOffset + count))
                layerOffset = Math.max(0, index - count + 1);
        }
        logic =
                packet.logic().isEmpty()
                        ? new LogicObservation(1, List.of())
                        : JsonCodecs.readLogicObservation(packet.logic());
        powered = packet.powered();
        held = packet.held();
        assembly = packet.assembly();
        wear = packet.wear();
        assembly.stats(WandResources.definitions());
        boolean refresh = reply == EditorRequests.Reply.REFRESH;
        if (!refresh) model.message = packet.message();
        if (minecraft != null
                && minecraft.screen == this
                && (!refresh || changed || previousSlot != slot || previousLayer != layer))
            rebuildWidgets();
    }

    private void attempt(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            model.message =
                    e.getMessage() == null
                            ? dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.screen._circuit_screen.004")
                            : e.getMessage();
        }
    }

    private CircuitButton widget(
            int x,
            int y,
            int w,
            int h,
            String text,
            boolean selected,
            Rune.Kind icon,
            boolean parent,
            Runnable action) {
        CircuitButton button =
                new CircuitButton(x, y, w, h, text, selected, icon, parent, () -> attempt(action));
        addRenderableWidget(button);
        return button;
    }

    private void button(int x, int y, int w, String text, EditorIcon icon, Runnable action) {
        button(x, y, w, text, icon, CircuitButton.Role.ACTION, action);
    }

    private void button(
            int x,
            int y,
            int w,
            String text,
            EditorIcon icon,
            CircuitButton.Role role,
            Runnable action) {
        if (x >= width - layout.right() + 6
                && (y < parameterTop() || y + 22 > layout.bottom() - 14)) return;
        widget(x, y, w, 22, text, false, null, false, action).icon(icon).role(role);
    }

    private int parameterTop() {
        return layout.top()
                + 24
                + (!model.components && model.selected() != null && height >= 360 ? 40 : 0);
    }

    private int parameterMaximumScroll() {
        int overflow = Math.max(0, contentHeight - (layout.bottom() - parameterTop() - 14));
        return (overflow + 23) / 24 * 24;
    }

    @Override
    protected void init() {
        layout = viewport.apply(baseLayout());
        model.parameters.bind(model.draft, model.selected);
        if (nativeCursor == null)
            nativeCursor = new NativeEditorCursor(minecraft.getWindow().getWindow());
        buildNavigation();
        int toolY = toolbarY();
        int toolsX = (width - 260) / 2;
        widget(
                        toolsX + 0,
                        toolY,
                        50,
                        21,
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._circuit_screen.005"),
                        model.tool == EditorModel.Tool.SELECT,
                        null,
                        false,
                        () -> {
                            model.tool = EditorModel.Tool.SELECT;
                            model.cancel();
                            rebuildWidgets();
                        })
                .icon(EditorIcon.action(EditorIcon.Action.SELECT));
        widget(
                        toolsX + 53,
                        toolY,
                        50,
                        21,
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._circuit_screen.006"),
                        model.tool == EditorModel.Tool.ROUTE,
                        null,
                        false,
                        () -> {
                            model.tool = EditorModel.Tool.ROUTE;
                            model.cancel();
                            rebuildWidgets();
                        })
                .icon(EditorIcon.action(EditorIcon.Action.ROUTE))
                .setTooltip(
                        Tooltip.create(
                                Component.literal(
                                        dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._circuit_screen.007"))));
        widget(
                toolsX + 106,
                toolY,
                40,
                21,
                model.payload == Route.Payload.MANA
                        ? dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._circuit_screen.008")
                        : dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._circuit_screen.009"),
                false,
                null,
                false,
                () -> {
                    model.payload =
                            model.payload == Route.Payload.MANA
                                    ? Route.Payload.SIGNAL
                                    : Route.Payload.MANA;
                    model.cancel();
                    model.sourcePort = 0;
                    rebuildWidgets();
                });
        widget(
                        toolsX + 203,
                        toolY,
                        57,
                        21,
                        dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._circuit_screen.010")
                                + (model.sourcePort + 1),
                        false,
                        null,
                        false,
                        () -> {
                            model.cancel();
                            model.sourcePort = (model.sourcePort + 1) % 3;
                            rebuildWidgets();
                        })
                .setTooltip(
                        Tooltip.create(
                                Component.literal(
                                        dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._circuit_screen.011"))));
        widget(
                        toolsX + 149,
                        toolY,
                        24,
                        21,
                        "",
                        false,
                        null,
                        false,
                        () -> {
                            model.draft.undo();
                            model.cancel();
                            model.selected = -1;
                            rebuildWidgets();
                        })
                .icon(EditorIcon.action(EditorIcon.Action.UNDO))
                .setTooltip(
                        Tooltip.create(
                                Component.literal(
                                        dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._circuit_screen.012"))));
        widget(
                        toolsX + 176,
                        toolY,
                        24,
                        21,
                        "",
                        false,
                        null,
                        false,
                        () -> {
                            model.draft.redo();
                            model.cancel();
                            model.selected = -1;
                            rebuildWidgets();
                        })
                .icon(EditorIcon.action(EditorIcon.Action.REDO))
                .setTooltip(
                        Tooltip.create(
                                Component.literal(
                                        dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._circuit_screen.013"))));
        int tab = width - layout.right() + 6, tabWidth = (layout.right() - 15) / 2;
        widget(
                tab,
                layout.top(),
                tabWidth,
                21,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._circuit_screen.014"),
                !model.components,
                null,
                false,
                () -> {
                    model.components = false;
                    model.parameterScroll = 0;
                    rebuildWidgets();
                });
        widget(
                tab + tabWidth + 3,
                layout.top(),
                tabWidth,
                21,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._circuit_screen.015"),
                false,
                null,
                false,
                () -> {
                    model.cancel();
                    BookClient.openNotebook();
                });
        widget(
                width - 48,
                5,
                40,
                18,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._circuit_screen.016"),
                false,
                null,
                false,
                this::onClose);
        buildPalette();
        buildParameters(
                tab,
                layout.top()
                        + 24
                        + (model.selected() == null || height < 360 ? 0 : 40)
                        - model.parameterScroll,
                layout.right() - 12);
        if (focusParameter) {
            for (var child : children())
                if (child instanceof EditBox box) {
                    setFocused(box);
                    break;
                }
            focusParameter = false;
        }
        int maxScroll = parameterMaximumScroll();
        if (model.parameterScroll > maxScroll) {
            model.parameterScroll = maxScroll;
            rebuildWidgets();
            return;
        }
        int y = height - 65, w = Math.min(86, (width - 28) / 4);
        widget(
                        8,
                        y,
                        w,
                        20,
                        engraving
                                ? dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._circuit_screen.017")
                                : dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._circuit_screen.018"),
                        false,
                        null,
                        false,
                        () -> send(0, JsonCodecs.blueprint(model.draft.get())))
                .role(CircuitButton.Role.PRIMARY);
        int actions = engraving ? 1 : 3,
                actionW = width < 440 ? 48 : 68,
                actionX = (width - actions * (actionW + 3) + 3) / 2;
        widget(
                        actionX,
                        5,
                        actionW,
                        18,
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._circuit_screen.019"),
                        false,
                        null,
                        false,
                        this::openBlueprints)
                .icon(EditorIcon.action(EditorIcon.Action.EXPORT));
        if (!engraving) {
            widget(
                            actionX + actionW + 3,
                            5,
                            actionW,
                            18,
                            dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.screen._circuit_screen.020"),
                            false,
                            null,
                            false,
                            () -> manage(0))
                    .role(CircuitButton.Role.NAVIGATION);
            widget(
                            actionX + 2 * (actionW + 3),
                            5,
                            actionW,
                            18,
                            dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.screen._circuit_screen.021"),
                            false,
                            null,
                            false,
                            this::layers)
                    .role(CircuitButton.Role.NAVIGATION);
        }
    }

    private void buildNavigation() {
        int n = inspection.slots().size(), w = Math.min(88, (width - 16) / Math.max(1, n));
        for (int i = 0; i < n; i++) {
            var s = inspection.slots().get(i);
            widget(
                                    (width - n * w + 3) / 2 + i * w,
                                    width < 440 ? 26 : 27,
                                    w - 3,
                                    width < 440 ? 18 : 21,
                                    dev.elementalcircuits.client.text.UiText.string(
                                                    "text.elementalcircuits.client.screen._circuit_screen.022")
                                            + s.id()
                                            + (s.id() == slot
                                                    ? dev.elementalcircuits.client.text.UiText
                                                            .string(
                                                                    "text.elementalcircuits.client.screen._circuit_screen.023")
                                                    : ""),
                                    s.id() == slot,
                                    null,
                                    false,
                                    () -> managementRequest(slot, 8, Integer.toString(s.id())))
                            .active =
                    !engraving;
        }
        var layers = inspection.layers();
        if (layers.isEmpty()) return;
        int count = Math.max(1, (width - 54) / 70);
        layerOffset = Math.max(0, Math.min(layerOffset, Math.max(0, layers.size() - count)));
        int x = (width - Math.min(count, layers.size()) * 70 + 6) / 2;
        for (int i = layerOffset; i < layers.size() && i < layerOffset + count; i++) {
            var l = layers.get(i);
            String text =
                    (l.id() == 1
                                    ? dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._circuit_screen.024")
                                    : dev.elementalcircuits.client.text.UiText.string(
                                                    "text.elementalcircuits.client.screen._circuit_screen.025")
                                            + l.id())
                            + (l.trigger() == null ? "" : " →" + l.trigger().target());
            widget(
                                    x,
                                    width < 440 ? 46 : 50,
                                    64,
                                    width < 440 ? 15 : 16,
                                    text,
                                    l.id() == layer,
                                    null,
                                    false,
                                    () -> layerRequest(l.id(), 17, ""))
                            .active =
                    !engraving;
            x += 70;
        }
        if (layers.size() > count) {
            widget(
                    width - 43,
                    width < 440 ? 46 : 50,
                    17,
                    width < 440 ? 15 : 16,
                    "‹",
                    false,
                    null,
                    false,
                    () -> {
                        layerOffset--;
                        rebuildWidgets();
                    });
            widget(
                    width - 24,
                    width < 440 ? 46 : 50,
                    17,
                    width < 440 ? 15 : 16,
                    "›",
                    false,
                    null,
                    false,
                    () -> {
                        layerOffset++;
                        rebuildWidgets();
                    });
        }
    }

    private void buildPalette() {
        List<RuneCatalog.Row> rows = RuneCatalog.rows(model.expanded);
        int visible = layout.visibleRows();
        model.paletteOffset =
                Math.max(0, Math.min(model.paletteOffset, Math.max(0, rows.size() - 1)));
        for (int i = 0; i < visible && i + model.paletteOffset < rows.size(); i++) {
            RuneCatalog.Row row = rows.get(i + model.paletteOffset);
            int y = layout.top() + i * 24;
            if (row.parent())
                widget(
                        8,
                        y,
                        layout.left() - 16,
                        22,
                        (model.expanded.contains(row.group()) ? "▾ " : "▸ ") + row.group().title(),
                        false,
                        null,
                        true,
                        () -> {
                            model.toggle(row.group());
                            rebuildWidgets();
                        });
            else {
                CircuitButton b =
                        widget(
                                        12,
                                        y,
                                        layout.left() - 20,
                                        22,
                                        row.title(),
                                        model.tool == EditorModel.Tool.PLACE
                                                && model.kind == row.kind()
                                                && (row.element() == null
                                                        || model.element == row.element()),
                                        row.kind(),
                                        false,
                                        () -> {
                                            model.choose(row.kind(), row.element());
                                            rebuildWidgets();
                                        })
                                .icon(EditorIcon.rune(row.kind(), row.element()));
                b.setTooltip(
                        Tooltip.create(
                                Component.literal(
                                        row.title()
                                                + "\n"
                                                + RuneCatalog.description(row.kind())
                                                + dev.elementalcircuits.client.text.UiText.string(
                                                        "text.elementalcircuits.client.screen._circuit_screen.026"))));
            }
        }
    }

    private void buildParameters(int x, int y, int w) {
        Rune rune = model.selected();
        if (rune == null) {
            button(
                    x,
                    y,
                    w,
                    model.draft.get().continuous()
                            ? dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.screen._circuit_screen.027")
                            : dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.screen._circuit_screen.028"),
                    EditorIcon.rune(Rune.Kind.INPUT),
                    CircuitButton.Role.TOGGLE,
                    () -> {
                        Blueprint b = model.draft.get();
                        model.draft.set(
                                new Blueprint(
                                        b.version(),
                                        b.rules(),
                                        b.runes(),
                                        b.routes(),
                                        !b.continuous()));
                        rebuildWidgets();
                    });
            if (!engraving)
                button(
                        x,
                        y + 24,
                        w,
                        dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._circuit_screen.029")
                                + materialTitle(model.draft.get().rules().material()),
                        EditorIcon.action(EditorIcon.Action.PLATE),
                        CircuitButton.Role.NAVIGATION,
                        () -> manage(2));
            contentHeight = 48;
            return;
        }
        int p = y;
        if (rune.kind() == Rune.Kind.CONVERT || rune.kind() == Rune.Kind.FILTER) {
            button(
                    x,
                    p,
                    w,
                    dev.elementalcircuits.client.text.UiText.string(
                            "ui.elementalcircuits.element",
                            dev.elementalcircuits.client.text.UiText.element(rune.element())),
                    EditorIcon.rune(Rune.Kind.CONVERT, rune.element()),
                    () -> {
                        Element[] elements = Element.values();
                        model.draft.replace(
                                new Rune(
                                        rune.id(),
                                        rune.kind(),
                                        rune.cell(),
                                        elements[(rune.element().ordinal() + 1) % elements.length],
                                        rune.value(),
                                        rune.on(),
                                        rune.signalControl(),
                                        rune.weights(),
                                        rune.storage(),
                                        rune.filter()));
                        rebuildWidgets();
                    });
            p += 24;
        }
        if (rune.kind() == Rune.Kind.SWITCH
                || rune.kind() == Rune.Kind.SIGNAL
                || rune.kind() == Rune.Kind.TOGGLE
                || rune.kind() == Rune.Kind.FILTER) {
            button(
                    x,
                    p,
                    w,
                    rune.kind() == Rune.Kind.FILTER
                            ? dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._circuit_screen.030")
                                    + (rune.on()
                                            ? dev.elementalcircuits.client.text.UiText.string(
                                                    "text.elementalcircuits.client.screen._circuit_screen.031")
                                            : dev.elementalcircuits.client.text.UiText.string(
                                                    "text.elementalcircuits.client.screen._circuit_screen.032"))
                            : dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._circuit_screen.033")
                                    + (rune.on()
                                            ? dev.elementalcircuits.client.text.UiText.string(
                                                    "text.elementalcircuits.client.screen._circuit_screen.034")
                                            : dev.elementalcircuits.client.text.UiText.string(
                                                    "text.elementalcircuits.client.screen._circuit_screen.035")),
                    EditorIcon.rune(Rune.Kind.SWITCH),
                    () -> {
                        model.draft.replace(
                                new Rune(
                                        rune.id(),
                                        rune.kind(),
                                        rune.cell(),
                                        rune.element(),
                                        rune.value(),
                                        !rune.on(),
                                        rune.signalControl(),
                                        rune.weights(),
                                        rune.storage(),
                                        rune.filter()));
                        rebuildWidgets();
                    });
            p += 24;
        }
        if (rune.kind().outputPorts(model.payload) > 1) {
            for (int port = 0; port < rune.kind().outputPorts(model.payload); port++) {
                int selectedPort = port;
                button(
                        x,
                        p,
                        w,
                        dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._circuit_screen.010")
                                + (port + 1)
                                + " · "
                                + rune.kind().outputLabel(model.payload, port),
                        EditorIcon.action(EditorIcon.Action.ROUTE),
                        () -> {
                            model.beginRoute(rune.id(), selectedPort);
                            rebuildWidgets();
                        });
                p += 24;
            }
        }
        if (rune.kind() == Rune.Kind.SWITCH) {
            button(
                    x,
                    p,
                    w,
                    rune.signalControl()
                            ? dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.screen._circuit_screen.036")
                            : dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.screen._circuit_screen.037"),
                    EditorIcon.rune(Rune.Kind.SWITCH),
                    () -> {
                        model.draft.replace(
                                new Rune(
                                        rune.id(),
                                        rune.kind(),
                                        rune.cell(),
                                        rune.element(),
                                        rune.value(),
                                        rune.on(),
                                        !rune.signalControl(),
                                        rune.weights(),
                                        rune.storage(),
                                        rune.filter()));
                        rebuildWidgets();
                    });
            p += 24;
            if (layer == 1)
                button(
                        x,
                        p,
                        w,
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._circuit_screen.038"),
                        EditorIcon.action(EditorIcon.Action.APPLY),
                        () -> send(3, Integer.toString(rune.id())));
            p += 24;
        }
        model.parameters.bind(model.draft, rune.id());
        if (RuneParameterEdit.supported(rune.kind())) {
            var edit = model.parameters;
            button(
                    x,
                    p,
                    w,
                    (edit.expanded ? "▾ " : "▸ ") + RuneParameterEdit.title(rune.kind()),
                    EditorIcon.action(EditorIcon.Action.APPLY),
                    CircuitButton.Role.PARAMETER,
                    () -> {
                        edit.expanded = !edit.expanded;
                        focusParameter = edit.expanded;
                        rebuildWidgets();
                    });
            p += 24;
            if (edit.expanded) {
                String[] labels =
                        rune.kind() == Rune.Kind.SENSOR
                                ? new String[] {
                                    dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._circuit_screen.039"),
                                    dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._circuit_screen.040")
                                }
                                : new String[] {
                                    dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._circuit_screen.041"),
                                    dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._circuit_screen.042"),
                                    dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._circuit_screen.043")
                                };
                for (int i = 0; i < edit.size(); i++) {
                    int index = i;
                    boolean storage = rune.kind() == Rune.Kind.STORAGE;
                    boolean labeled = storage || rune.kind() == Rune.Kind.SENSOR;
                    int inset = labeled ? 30 : 0;
                    if (p >= parameterTop() && p + 20 <= layout.bottom() - 14) {
                        if (labeled)
                            addRenderableWidget(
                                    new StringWidget(
                                            x, p, 28, 20, Component.literal(labels[i]), font));
                        EditBox box =
                                new EditBox(
                                        font,
                                        x + inset,
                                        p,
                                        w - inset,
                                        20,
                                        Component.literal(
                                                storage
                                                        ? labels[i]
                                                        : RuneParameterEdit.title(rune.kind())));
                        box.setMaxLength(256);
                        box.setValue(edit.value(i));
                        box.setResponder(text -> edit.value(index, text));
                        addRenderableWidget(box);
                    }
                    p += 24;
                }
                if (rune.kind() == Rune.Kind.STORAGE) {
                    button(
                            x,
                            p,
                            w,
                            edit.signal
                                    ? dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._circuit_screen.044")
                                    : dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._circuit_screen.045"),
                            EditorIcon.rune(Rune.Kind.SIGNAL),
                            CircuitButton.Role.TOGGLE,
                            () -> {
                                edit.signal = !edit.signal;
                                rebuildWidgets();
                            });
                    p += 24;
                }
                button(
                        x,
                        p,
                        w,
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._circuit_screen.046"),
                        EditorIcon.action(EditorIcon.Action.APPLY),
                        CircuitButton.Role.PRIMARY,
                        this::saveParameter);
                p += 24;
            }
        }
        if (layer == 1 && RuneLogic.sequential(rune.kind())) {
            button(
                    x,
                    p,
                    w,
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.screen._circuit_screen.040"),
                    EditorIcon.action(EditorIcon.Action.UNDO),
                    () -> send(13, Integer.toString(rune.id())));
            p += 24;
        }
        button(
                x,
                p,
                w,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._circuit_screen.047"),
                EditorIcon.action(EditorIcon.Action.DELETE),
                CircuitButton.Role.DANGER,
                () -> {
                    model.draft.delete(model.selected);
                    model.selected = -1;
                    model.cancel();
                    rebuildWidgets();
                });
        p += 24;
        button(
                x,
                p,
                w,
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._circuit_screen.048"),
                EditorIcon.action(EditorIcon.Action.ROUTE),
                CircuitButton.Role.DANGER,
                () -> {
                    Blueprint b = model.draft.get();
                    model.draft.set(
                            new Blueprint(
                                    b.version(),
                                    b.rules(),
                                    b.runes(),
                                    b.routes().stream()
                                            .filter(
                                                    r ->
                                                            r.source() != model.selected
                                                                    && r.target() != model.selected)
                                            .toList(),
                                    b.continuous()));
                    model.message =
                            dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.screen._circuit_screen.049");
                });
        contentHeight = p - y + 24;
    }

    private void send(int action, String text) {
        managementRequest(slot, action, text);
    }

    @Override
    public void tick() {
        super.tick();
        if (requests.tick())
            model.message =
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.screen._circuit_screen.050");
        if (minecraft.player != null
                && !requests.waiting()
                && ++refreshTicks
                        >= (model.selected() != null
                                        && RuneLogic.sequential(model.selected().kind())
                                ? 5
                                : 20)) {
            refreshTicks = 0;
            send(5, "");
        }
    }

    private void saveParameter() {
        model.parameters.apply(model.draft);
        model.parameterScroll = 0;
        model.message =
                dev.elementalcircuits.client.text.UiText.string(
                        "text.elementalcircuits.client.screen._circuit_screen.051");
        rebuildWidgets();
    }

    @Override
    public boolean mouseClicked(double x, double y, int mouse) {
        if (mouse == 2 && layout.onBoard(x, y)) {
            panning = true;
            return true;
        }
        if (super.mouseClicked(x, y, mouse)) return true;
        if (mouse == 1) {
            model.cancel();
            boardPress = false;
            return true;
        }
        Hex cell = layout.cell(x, y);
        if (cell == null) return false;
        if (model.components) return true;
        if (mouse == 0) {
            boardPress = model.tool == EditorModel.Tool.SELECT;
            pressX = x;
            pressY = y;
            attempt(
                    () -> {
                        model.click(cell);
                        if (!boardPress) rebuildWidgets();
                    });
        }
        return true;
    }

    @Override
    public void mouseMoved(double x, double y) {
        super.mouseMoved(x, y);
        pointerX = x;
        pointerY = y;
        updateCursor(x, y);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (panning && button == 2) {
            viewport.pan(baseLayout(), dx, dy);
            layout = viewport.apply(baseLayout());
            return true;
        }
        if (boardPress && button == 0 && !model.components) {
            if (!model.dragging() && Math.hypot(x - pressX, y - pressY) > 3) model.beginDrag();
            return true;
        }
        if (!model.components
                && model.tool == EditorModel.Tool.ROUTE
                && model.drawing()
                && button == 0) {
            Hex h = layout.cell(x, y);
            if (h != null && model.draft.at(h) == null) attempt(() -> model.click(h));
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == 2 && panning) {
            panning = false;
            return true;
        }
        if (button == 0 && boardPress) {
            boardPress = false;
            if (model.dragging()) attempt(() -> model.drop(layout.cell(x, y)));
            rebuildWidgets();
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double delta) {
        if (x < layout.left()) {
            model.paletteOffset += delta > 0 ? -1 : 1;
            rebuildWidgets();
            return true;
        }
        if (x > width - layout.right()) {
            model.parameterScroll =
                    Math.max(
                            0,
                            Math.min(
                                    parameterMaximumScroll(),
                                    model.parameterScroll + (delta > 0 ? -24 : 24)));
            rebuildWidgets();
            return true;
        }
        if (layout.onBoard(x, y)) {
            viewport.zoom(baseLayout(), x, y, delta);
            layout = viewport.apply(baseLayout());
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && (model.drawing() || model.dragging())) {
            model.cancel();
            boardPress = false;
            return true;
        }
        if (model.parameters.expanded
                && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            attempt(this::saveParameter);
            return true;
        }
        if (!(getFocused() instanceof EditBox)) {
            if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)
                    && !hasControlDown()
                    && !hasAltDown()) {
                send(0, JsonCodecs.blueprint(model.draft.get()));
                return true;
            }
            if (key == GLFW.GLFW_KEY_DELETE && model.tool == EditorModel.Tool.ROUTE) {
                attempt(
                        () -> {
                            if (model.deleteRoute(hoveredWire(pointerX, pointerY)))
                                rebuildWidgets();
                        });
                return true;
            }
            if (key == GLFW.GLFW_KEY_DELETE
                    && model.tool == EditorModel.Tool.SELECT
                    && model.selected > 0) {
                attempt(
                        () -> {
                            model.draft.delete(model.selected);
                            model.selected = -1;
                            model.cancel();
                            rebuildWidgets();
                        });
                return true;
            }
            if (key == GLFW.GLFW_KEY_HOME) {
                viewport.reset();
                layout = viewport.apply(baseLayout());
                return true;
            }
            if (hasControlDown() && (key == GLFW.GLFW_KEY_Z || key == GLFW.GLFW_KEY_Y)) {
                if (key == GLFW.GLFW_KEY_Z) model.draft.undo();
                else model.draft.redo();
                model.selected = -1;
                model.cancel();
                rebuildWidgets();
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft.player != null) CircuitNetwork.request(identity, slot, 14, "");
        super.onClose();
    }

    // Screen.render invokes the vanilla background in 1.21; our renderer already painted it.
    @Override
    public void renderBackground(GuiGraphics g, int x, int y, float partial) {}

    private Blueprint complexityDefinition;

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        pointerX = mouseX;
        pointerY = mouseY;
        String status =
                (engraving
                                ? materialTitle(model.draft.get().rules().material())
                                        + dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._circuit_screen.052")
                                : fixed
                                        ? dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._circuit_screen.053")
                                        : book
                                                ? dev.elementalcircuits.client.text.UiText.string(
                                                        "text.elementalcircuits.client.screen._circuit_screen.054")
                                                : gear
                                                        ? dev.elementalcircuits.client.text.UiText
                                                                .string(
                                                                        "text.elementalcircuits.client.screen._circuit_screen.055")
                                                        : dev.elementalcircuits.client.text.UiText
                                                                .string(
                                                                        "text.elementalcircuits.client.screen._circuit_screen.056"))
                        + dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._circuit_screen.057")
                        + slot
                        + " · "
                        + (layer == 1
                                ? dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._circuit_screen.024")
                                : dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._circuit_screen.025")
                                        + layer)
                        + " · "
                        + (layer != 1
                                ? dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.screen._circuit_screen.058")
                                : powered
                                        ? dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._circuit_screen.059")
                                        : dev.elementalcircuits.client.text.UiText.string(
                                                "text.elementalcircuits.client.screen._circuit_screen.060"));
        if (held > 0)
            status +=
                    dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.screen._circuit_screen.061")
                            + Fixed.display(held);
        if (!model.draft.get().equals(serverBlueprint))
            status +=
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.screen._circuit_screen.062");
        final String displayStatus = status;
        EditorModel.View view = model.view(layout.cell(mouseX, mouseY));
        // In 1.20.1 each unmanaged fill flushes its buffer. Batch board strokes together;
        // scissor changes inside paint flush at the required boundaries automatically.
        if (complexityDefinition != model.draft.get()) {
            complexityDefinition = model.draft.get();
            var complexity = CircuitComplexity.of(complexityDefinition);
            int delay =
                    complexityDefinition.rules().casting().standard().delay(complexity, Fixed.ONE);
            complexityStatus =
                    dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.screen._circuit_screen.063")
                            + Fixed.display(complexity.score())
                            + dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.screen._circuit_screen.064")
                            + delay
                            + dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.screen._circuit_screen.065");
        }
        g.drawManaged(
                () -> {
                    EditorRenderer.paint(
                            g, font, layout, view, displayStatus + complexityStatus, logicStatus());
                    if (model.tool == EditorModel.Tool.ROUTE && !model.drawing())
                        EditorRenderer.hoverRoute(g, layout, hoveredWire(mouseX, mouseY));
                });
        super.render(g, mouseX, mouseY, partial);
        boolean custom = !model.components && layout.onBoard(mouseX, mouseY);
        updateCursor(mouseX, mouseY);
        if (custom) EditorRenderer.pointerHint(g, font, view, mouseX, mouseY);
    }

    private String logicStatus() {
        Rune selected = model.selected();
        if (selected == null || !RuneLogic.sequential(selected.kind())) return "";
        var state = logic.find(selected.id());
        if (state == null || state.kind() != selected.kind())
            return dev.elementalcircuits.client.text.UiText.string(
                    "text.elementalcircuits.client.screen._circuit_screen.066");
        return switch (state.kind()) {
            case COUNTER ->
                    dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.screen._circuit_screen.067")
                            + state.count()
                            + "/"
                            + state.parameter();
            case CLOCK ->
                    dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.screen._circuit_screen.068")
                            + state.phase()
                            + "/"
                            + state.parameter();
            case TOGGLE ->
                    dev.elementalcircuits.client.text.UiText.string(
                                    "text.elementalcircuits.client.screen._circuit_screen.069")
                            + (state.latched()
                                    ? dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._circuit_screen.034")
                                    : dev.elementalcircuits.client.text.UiText.string(
                                            "text.elementalcircuits.client.screen._circuit_screen.035"));
            default -> "";
        };
    }

    private void updateCursor(double x, double y) {
        if (nativeCursor == null || layout == null) return;
        if (!model.components && layout.onBoard(x, y))
            nativeCursor.show(
                    model.view(layout.cell(x, y)),
                    (int) Math.ceil(minecraft.getWindow().getGuiScale()));
        else nativeCursor.restore();
    }

    @Override
    public void removed() {
        model.cancel();
        boardPress = false;
        if (nativeCursor != null) {
            nativeCursor.close();
            nativeCursor = null;
        }
        super.removed();
    }

    private static String materialTitle(Rules.Material m) {
        return switch (m) {
            case WOOD ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.screen._circuit_screen.070");
            case BRASS ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.screen._circuit_screen.071");
            case CRYSTAL ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.screen._circuit_screen.072");
        };
    }
}
