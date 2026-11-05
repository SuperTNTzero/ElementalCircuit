package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.codec.BlueprintTextCode;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.domain.book.BlueprintVolume;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.mana.StorageSettings;
import dev.elementalcircuits.testing.TestBlueprints;

import java.util.*;

public final class EditorInteractionContracts {
    private static int checks;

    private static void check(boolean b, String message) {
        checks++;
        if (!b) throw new AssertionError(message);
    }

    public static int run() {
        checks = 0;
        var entries = TestBlueprints.entries();
        check(
                entries.size() == 16,
                "Previous templates and connection example are blueprint files");
        var volume = TestBlueprints.fill(BlueprintVolume.empty(new UUID(38, 1)));
        check(
                volume.pages().size() == entries.size() && volume.selected() == 1,
                "Test injection preserves file order and selects the first page");
        check(
                JsonCodecs.readVolume(JsonCodecs.volume(volume)).equals(volume),
                "File-backed notebook round trip");
        for (var entry : entries)
            check(
                    BlueprintTextCode.decode(BlueprintTextCode.encode(entry.blueprint()))
                            .equals(entry.blueprint()),
                    "Every file imports through the public text codec");
        var board =
                entries.stream()
                        .filter(e -> e.file().equals("storage.ecblueprint"))
                        .findFirst()
                        .orElseThrow()
                        .blueprint();
        var model = new EditorModel(board);
        model.expanded.addAll(EnumSet.allOf(RuneCatalog.Group.class));
        model.paletteOffset = 9;
        model.toggle(RuneCatalog.Group.EFFECT);
        check(model.paletteOffset == 9, "Collapse keeps the current catalog viewport");
        model.toggle(RuneCatalog.Group.EFFECT);
        check(model.paletteOffset == 9, "Expand keeps the current catalog viewport");
        var edit = model.parameters;
        edit.bind(model.draft, 1);
        check(!edit.expanded, "Parameters start folded");
        edit.expanded = true;
        edit.value(0, "10000");
        edit.bind(model.draft, 1);
        check(edit.value(0).equals("10000") && edit.expanded, "Widget rebuilds keep unsaved text");
        edit.apply(model.draft);
        check(
                model.draft.get().inputAmount(model.draft.get().rune(1)) == 10000 * Fixed.ONE,
                "Inline input is not clipped to legacy capacity");
        model.draft.undo();
        check(model.draft.get().equals(board), "Parameter save is one undo operation");
        edit.bind(model.draft, 2);
        edit.expanded = true;
        edit.value(0, "20000");
        edit.value(1, "120");
        edit.value(2, "12.5");
        edit.signal = true;
        edit.apply(model.draft);
        check(
                model.draft
                                .get()
                                .rune(2)
                                .storage()
                                .equals(
                                        new StorageSettings(
                                                20000 * Fixed.ONE,
                                                120 * Fixed.ONE,
                                                25 * Fixed.ONE / 2))
                        && model.draft.get().rune(2).signalControl(),
                "Inline storage saves all fields atomically");
        edit.bind(model.draft, 2);
        edit.value(1, "30000");
        var before = model.draft.get();
        boolean rejected = false;
        try {
            edit.apply(model.draft);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(
                rejected && model.draft.get().equals(before),
                "Invalid storage leaves the whole draft unchanged");
        edit.bind(model.draft, 1);
        check(!edit.expanded && edit.size() == 1, "Changing selection closes the previous form");
        model.draft.set(board);
        var l = EditorLayout.of(960, 600, board.rules().boardRadius(), 108);
        var route = board.routes().getFirst();
        var a = route.path().getFirst();
        var b = route.path().get(1);
        double x = (l.x(a) + l.x(b)) / 2, y = (l.y(a) + l.y(b)) / 2;
        // Pick tests use a long straight route with an empty intermediate cell, outside rune
        // footprints.
        var runes =
                List.of(
                        Rune.create(1, Rune.Kind.AND, new Hex(-2, 0)),
                        Rune.create(2, Rune.Kind.OR, new Hex(2, 0)));
        route =
                new Route(
                        1,
                        2,
                        Route.Payload.MANA,
                        List.of(
                                new Hex(-2, 0),
                                new Hex(-1, 0),
                                new Hex(0, 0),
                                new Hex(1, 0),
                                new Hex(2, 0)));
        var signal = new Route(1, 2, Route.Payload.SIGNAL, route.path());
        board = new Blueprint(5, board.rules(), runes, List.of(route, signal), false);
        model = new EditorModel(board);
        x = l.x(new Hex(0, 0));
        y = l.y(new Hex(0, 0));
        check(
                RouteHitTest.at(board, l, Route.Payload.MANA, x, y).equals(route),
                "Pick individual mana line at overlap");
        check(
                RouteHitTest.at(board, l, Route.Payload.SIGNAL, x, y).equals(signal),
                "Signal tool picks the signal line");
        check(
                RouteHitTest.at(board, l, Route.Payload.MANA, x, y + 8) == null,
                "Do not select nearby empty space");
        check(
                RouteHitTest.at(
                                board,
                                l,
                                Route.Payload.MANA,
                                l.x(new Hex(-2, 0)),
                                l.y(new Hex(-2, 0)))
                        == null,
                "Rune endpoints are protected");
        check(
                !model.deleteRoute(route) && model.draft.get().equals(board),
                "Select mode cannot delete a line");
        model.tool = EditorModel.Tool.PLACE;
        check(!model.deleteRoute(route), "Place mode cannot delete a line");
        model.tool = EditorModel.Tool.ROUTE;
        model.click(new Hex(-2, 0));
        check(!model.deleteRoute(route), "In-progress wire cannot delete other lines");
        model.cancel();
        check(!model.deleteRoute(signal), "Current payload guards other line types");
        check(model.deleteRoute(route), "Route mode deletes one hovered wire");
        check(
                model.draft.get().routes().equals(List.of(signal))
                        && model.draft.get().runes().equals(runes),
                "Delete preserves other wires and both runes");
        model.draft.undo();
        check(model.draft.get().equals(board), "One Ctrl+Z restores the exact wire and ordering");
        model.draft.redo();
        check(model.draft.get().routes().equals(List.of(signal)), "Redo only deletes that wire");
        sparseIdentifiers();
        splitValidation();
        return checks;
    }

    private static void sparseIdentifiers() {
        var rules = dev.elementalcircuits.adapter.resources.RuleResources.defaults();
        var first = Rune.create(1025, Rune.Kind.INPUT, new Hex(-1, 0));
        var last = Rune.create(Integer.MAX_VALUE, Rune.Kind.OUTPUT, new Hex(1, 0));
        var b = new Blueprint(5, rules, List.of(first, last), List.of(), false);
        var m = new EditorModel(b);
        m.tool = EditorModel.Tool.ROUTE;
        m.click(first.cell());
        var preview = m.view(last.cell());
        check(preview.valid() && preview.preview().size() == 3, "合法编号1025不能与布线预览占位编号冲突");
        m.click(last.cell());
        check(m.draft.get().routes().size() == 1, "预览和实际连线均支持稀疏大编号");
        m.draft.place(Rune.Kind.INPUT, new Hex(0, 1));
        check(m.draft.at(new Hex(0, 1)).id() == 1, "最大int编号不阻止继续添加符文");
        var changed = m.draft.get();
        m.draft.undo();
        check(m.draft.get().runes().size() == 2, "编号重用不影响撤销");
        m.draft.redo();
        check(m.draft.get().equals(changed), "编号重用不影响重做和线路端点");
    }

    private static void splitValidation() {
        var rules = dev.elementalcircuits.adapter.resources.RuleResources.defaults();
        var a = Rune.create(2, Rune.Kind.OUTPUT, new Hex(1, 0));
        var b = Rune.create(3, Rune.Kind.OUTPUT, new Hex(0, 1));
        var first = new Route(1, 2, Route.Payload.MANA, List.of(new Hex(0, 0), a.cell()));
        var second = new Route(1, 3, Route.Payload.MANA, List.of(new Hex(0, 0), b.cell()));
        for (int mask = 0; mask < 8; mask++)
            for (int count = 0; count <= 2; count++) {
                var weights = List.of(mask & 1, mask >> 1 & 1, mask >> 2 & 1);
                var split =
                        new Rune(
                                1,
                                Rune.Kind.SPLIT,
                                new Hex(0, 0),
                                dev.elementalcircuits.domain.combat.Element.PURE,
                                0,
                                true,
                                false,
                                weights);
                var wires = List.of(first, second).subList(0, count);
                boolean rejected = false;
                try {
                    new Blueprint(5, rules, List.of(split, a, b), wires, false);
                } catch (IllegalArgumentException expected) {
                    rejected = true;
                }
                boolean positive = weights.subList(0, count).stream().anyMatch(w -> w > 0);
                check(rejected == (count > 0 && !positive), "只验证已连接出口的有序权重，未连接的正权重不能代付");
            }
        var defaults = Rune.create(1, Rune.Kind.SPLIT, new Hex(0, 0));
        check(
                new Blueprint(5, rules, List.of(defaults, a, b), List.of(second, first), false)
                        .routes()
                        .equals(List.of(second, first)),
                "隐式出口权重和原有线路顺序保持不变");
    }
}
