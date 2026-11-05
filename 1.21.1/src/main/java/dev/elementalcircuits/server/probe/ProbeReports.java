package dev.elementalcircuits.server.probe;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.CarrierRepository;
import dev.elementalcircuits.application.observation.SurveyReport;
import dev.elementalcircuits.block.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.CircuitAssembly;
import dev.elementalcircuits.domain.mana.ManaField;
import dev.elementalcircuits.server.mana.WorldManaField;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Read-only report assembly. In particular, no WorldManaField.discover or circuit commands. */
public final class ProbeReports {
    private ProbeReports() {}

    private static void row(List<SurveyReport.Row> rows, String key, String value) {
        rows.add(new SurveyReport.Row(key, value));
    }

    private static String location(ServerLevel level, BlockPos p) {
        return p.getX() + ", " + p.getY() + ", " + p.getZ() + " · " + level.dimension().location();
    }

    private static String field(ServerLevel level, BlockPos p) {
        var data = WorldManaField.get(level);
        if (!data.valid())
            return dev.elementalcircuits.text.Texts.get(
                    "text.elementalcircuits.server.probe._probe_reports.001");
        var f = data.field();
        var c = ManaField.Cell.at(p.getX(), p.getY(), p.getZ());
        return f.cells().contains(c)
                ? Fixed.display(f.amount(c)) + " / " + Fixed.display(f.rules().capacity())
                : dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.server.probe._probe_reports.002");
    }

    public static SurveyReport block(ServerLevel level, BlockPos pos) {
        List<SurveyReport.Row> rows = new ArrayList<>();
        String title = level.getBlockState(pos).getBlock().getName().getString();
        var block = level.getBlockEntity(pos);
        if (block instanceof CircuitBlockEntity circuit) {
            title =
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.probe._probe_reports.003");
            row(
                    rows,
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.probe._probe_reports.004"),
                    Fixed.display(circuit.available())
                            + " / "
                            + Fixed.display(circuit.assembly().rules().blockCapacity()));
            assembly(rows, circuit.assembly());
        } else if (block instanceof ManaNodeBlockEntity node) {
            title =
                    node.aerial()
                            ? dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.probe._probe_reports.005")
                            : node.natural()
                                    ? dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.server.probe._probe_reports.006")
                                    : dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.server.probe._probe_reports.007");
            if (!node.usable())
                row(
                        rows,
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.probe._probe_reports.008"),
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.probe._probe_reports.009"));
            else {
                row(
                        rows,
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.probe._probe_reports.010"),
                        Fixed.display(node.remaining()) + " / " + Fixed.display(node.capacity()));
                var data = WorldManaField.get(level);
                row(
                        rows,
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.probe._probe_reports.011"),
                        data.valid()
                                ? Fixed.display(
                                                node.emission()
                                                        * 20
                                                        / data.field().rules().stepTicks())
                                        + dev.elementalcircuits.text.Texts.get(
                                                "text.elementalcircuits.server.probe._probe_reports.012")
                                : dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.probe._probe_reports.001"));
                row(
                        rows,
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.probe._probe_reports.008"),
                        node.remaining() == 0
                                ? node.natural()
                                        ? dev.elementalcircuits.text.Texts.get(
                                                "text.elementalcircuits.server.probe._probe_reports.013")
                                        : dev.elementalcircuits.text.Texts.get(
                                                "text.elementalcircuits.server.probe._probe_reports.014")
                                : !data.valid()
                                        ? dev.elementalcircuits.text.Texts.get(
                                                "text.elementalcircuits.server.probe._probe_reports.015")
                                        : ServerSettings.count("field.enabled") == 0
                                                ? dev.elementalcircuits.text.Texts.get(
                                                        "text.elementalcircuits.server.probe._probe_reports.016")
                                                : dev.elementalcircuits.text.Texts.get(
                                                        "text.elementalcircuits.server.probe._probe_reports.017"));
            }
        }
        row(
                rows,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.server.probe._probe_reports.018"),
                field(level, pos));
        return new SurveyReport(1, title, location(level, pos), rows, "", 0, 0, true);
    }

    public static boolean carrier(ItemStack stack) {
        return stack.is(ElementalCircuits.WAND.get())
                || stack.is(ElementalCircuits.SPELL_BOOK.get())
                || stack.is(ElementalCircuits.CIRCUIT_ITEM.get());
    }

    public static SurveyReport item(ServerLevel level, BlockPos pos, ItemStack stack) {
        List<SurveyReport.Row> rows = new ArrayList<>();
        assembly(rows, CarrierRepository.load(stack));
        row(
                rows,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.server.probe._probe_reports.018"),
                field(level, pos));
        return new SurveyReport(
                1, stack.getHoverName().getString(), location(level, pos), rows, "", 0, 0, true);
    }

    private static void assembly(List<SurveyReport.Row> rows, CircuitAssembly a) {
        row(
                rows,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.server.probe._probe_reports.019"),
                a.selected() + " / " + a.slots().size());
        row(
                rows,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.server.probe._probe_reports.020"),
                Integer.toString(a.links().connections().size()));
        for (int id : a.slots()) {
            var c = a.circuit(id);
            String m =
                    switch (c.definition().rules().material()) {
                        case WOOD ->
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.probe._probe_reports.021");
                        case BRASS ->
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.probe._probe_reports.022");
                        case CRYSTAL ->
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.probe._probe_reports.023");
                    };
            row(
                    rows,
                    String.format(
                                    Locale.ROOT,
                                    dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.server.probe._probe_reports.024"),
                                    id)
                            + (id == a.selected()
                                    ? dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.server.probe._probe_reports.025")
                                    : ""),
                    m
                            + " · "
                            + (c.powered()
                                    ? dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.server.probe._probe_reports.026")
                                    : dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.server.probe._probe_reports.027"))
                            + " · "
                            + Fixed.display(c.held())
                            + dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.probe._probe_reports.028"));
            if (c.failures() > 0)
                row(
                        rows,
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.probe._probe_reports.029"),
                        c.failures() + " / " + Fixed.display(c.refunded()));
            var layers = a.layers(id);
            row(
                    rows,
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.probe._probe_reports.030"),
                    (layers.ids().size() - 1)
                            + " / "
                            + ServerSettings.layerRules().depth(c.definition().rules().material()));
            row(
                    rows,
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.probe._probe_reports.031"),
                    c.definition().runes().size() + " / " + c.definition().routes().size());
            var t = a.layers(id).trigger(1);
            if (t != null)
                row(
                        rows,
                        dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.probe._probe_reports.032")
                                + t.target(),
                        (switch (t.when()) {
                                    case IMPACT ->
                                            dev.elementalcircuits.text.Texts.get(
                                                    "text.elementalcircuits.server.probe._probe_reports.033");
                                    case TIMER ->
                                            t.delay()
                                                    + dev.elementalcircuits.text.Texts.get(
                                                            "text.elementalcircuits.server.probe._probe_reports.034");
                                })
                                + dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.probe._probe_reports.035")
                                + t.percent()
                                + "%");
        }
    }
}
