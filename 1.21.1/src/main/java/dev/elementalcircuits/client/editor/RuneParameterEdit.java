package dev.elementalcircuits.client.editor;

import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.RuneParameterValue;
import dev.elementalcircuits.domain.mana.StorageSettings;

import java.math.BigDecimal;
import java.util.*;

/** Unsaved inline parameter text belongs to the selected board, never to transient widgets. */
public final class RuneParameterEdit {
    private int selected = -1;
    public boolean expanded, signal;
    private final List<String> values = new ArrayList<>();

    public static boolean supported(Rune.Kind kind) {
        return switch (kind) {
            case STORAGE,
                            DELAY,
                            TARGET,
                            PROBE,
                            FORCE,
                            CONSTRAINT,
                            ANGLE,
                            YAW,
                            PITCH,
                            DISTANCE,
                            ITEM_INPUT,
                            ITEM_OUTPUT,
                            REDSTONE,
                            INPUT,
                            ABSORB,
                            SENSOR,
                            EDGE,
                            THROTTLE,
                            MATERIAL_FILTER,
                            AMOUNT,
                            RANGE,
                            RADIUS,
                            AIM,
                            SPLIT,
                            COUNTER,
                            CLOCK,
                            BURST,
                            BOUNCE,
                            HOMING,
                            FRAGMENT,
                            CHAIN,
                            IGNITE,
                            SLOW,
                            KNOCKBACK ->
                    true;
            default -> false;
        };
    }

    public void bind(EditorDraft draft, int id) {
        if (selected == id) return;
        selected = id;
        expanded = false;
        values.clear();
        if (id < 0) return;
        Rune r = draft.get().rune(id);
        signal = r.signalControl();
        if (r.kind() == Rune.Kind.STORAGE) {
            var s = r.storage();
            values.add(format(s.capacity()));
            values.add(format(s.threshold()));
            values.add(format(s.release()));
        } else if (r.kind() == Rune.Kind.SENSOR) {
            values.add(format(r.value()));
            values.add(format(r.weights().isEmpty() ? 0 : r.weights().getFirst()));
        } else if (r.kind() == Rune.Kind.MATERIAL_FILTER) {
            values.add(r.filter());
        } else
            values.add(
                    r.kind() == Rune.Kind.SPLIT
                            ? r.weights()
                                    .toString()
                                    .replace("[", "")
                                    .replace("]", "")
                                    .replace(" ", "")
                            : format(
                                    r.kind() == Rune.Kind.INPUT || r.kind() == Rune.Kind.ABSORB
                                            ? draft.get().inputAmount(r)
                                            : r.value()));
    }

    public int size() {
        return values.size();
    }

    public String value(int index) {
        return values.get(index);
    }

    public void value(int index, String text) {
        values.set(index, text);
    }

    public void apply(EditorDraft draft) {
        Rune r = draft.get().rune(selected);
        long value = r.value();
        List<Integer> weights = r.weights();
        var storage = r.storage();
        String filter = r.filter();
        if (r.kind() == Rune.Kind.STORAGE)
            storage =
                    new StorageSettings(
                            RuneParameterValue.parse(Rune.Kind.INPUT, value(0)),
                            RuneParameterValue.parse(Rune.Kind.INPUT, value(1)),
                            RuneParameterValue.parse(Rune.Kind.INPUT, value(2)));
        else if (r.kind() == Rune.Kind.SENSOR && values.size() > 1) {
            value = RuneParameterValue.parse(Rune.Kind.SENSOR, value(0));
            weights =
                    List.of(Math.toIntExact(RuneParameterValue.parse(Rune.Kind.SENSOR, value(1))));
        } else if (r.kind() == Rune.Kind.MATERIAL_FILTER) {
            filter = value(0).trim();
        } else if (r.kind() == Rune.Kind.SPLIT) {
            weights = new ArrayList<>();
            for (String part : value(0).split(",", -1)) weights.add(Integer.parseInt(part.trim()));
        } else value = RuneParameterValue.parse(r.kind(), value(0));
        if (r.kind() == Rune.Kind.INPUT || r.kind() == Rune.Kind.ABSORB)
            draft.inputAmount(r.id(), value);
        else
            draft.replace(
                    new Rune(
                            r.id(),
                            r.kind(),
                            r.cell(),
                            r.element(),
                            value,
                            r.on(),
                            r.kind() == Rune.Kind.STORAGE ? signal : r.signalControl(),
                            weights,
                            storage,
                            filter));
        expanded = false;
        selected = -1;
    }

    private static String format(long value) {
        return BigDecimal.valueOf(value)
                .divide(BigDecimal.valueOf(Fixed.ONE))
                .stripTrailingZeros()
                .toPlainString();
    }

    public static String title(Rune.Kind kind) {
        return switch (kind) {
            case DELAY ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.001");
            case TARGET ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.002");
            case PROBE ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.003");
            case ANGLE ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.004");
            case YAW ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.005");
            case PITCH ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.006");
            case DISTANCE ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.007");
            case FORCE, CONSTRAINT ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.008");
            case ITEM_INPUT, ITEM_OUTPUT ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.009");
            case REDSTONE ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.010");
            case STORAGE ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.011");
            case INPUT ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.012");
            case ABSORB ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.013");
            case SENSOR ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.014");
            case EDGE ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.015");
            case THROTTLE ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.016");
            case MATERIAL_FILTER ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.017");
            case SPLIT ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.018");
            case COUNTER ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.019");
            case CLOCK ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.020");
            case RANGE ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.021");
            case HOMING ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.022");
            case RADIUS, BURST ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.023");
            case BOUNCE ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.024");
            case FRAGMENT ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.025");
            case CHAIN ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.026");
            case IGNITE, SLOW ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.027");
            case KNOCKBACK ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.028");
            case AIM ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.029");
            default ->
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._rune_parameter_edit.030");
        };
    }
}
