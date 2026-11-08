package dev.elementalcircuits.client.editor;

import dev.elementalcircuits.client.text.UiText;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.combat.Element;

import java.util.*;

/** Semantic tree and labels, independent from Minecraft widgets and painting. */
public final class RuneCatalog {
    public enum Group {
        FLOW(
                "rune_group.elementalcircuits.flow",
                Rune.Kind.INPUT,
                Rune.Kind.ABSORB,
                Rune.Kind.STORAGE,
                Rune.Kind.FILTER,
                Rune.Kind.THROTTLE,
                Rune.Kind.SPLIT,
                Rune.Kind.MERGE,
                Rune.Kind.OUTPUT,
                Rune.Kind.MANA_RECEIVE,
                Rune.Kind.MANA_SEND),
        ELEMENT("rune_group.elementalcircuits.element"),
        LOGIC(
                "rune_group.elementalcircuits.logic",
                Rune.Kind.AND,
                Rune.Kind.OR,
                Rune.Kind.NOT,
                Rune.Kind.SIGNAL,
                Rune.Kind.SWITCH,
                Rune.Kind.SELECT,
                Rune.Kind.DELAY,
                Rune.Kind.PROBE,
                Rune.Kind.SENSOR,
                Rune.Kind.SIGNAL_RECEIVE,
                Rune.Kind.SIGNAL_SEND,
                Rune.Kind.COUNTER,
                Rune.Kind.CLOCK,
                Rune.Kind.TOGGLE,
                Rune.Kind.EDGE),
        DELIVERY(
                "rune_group.elementalcircuits.delivery",
                Rune.Kind.AREA,
                Rune.Kind.DIRECTED,
                Rune.Kind.CONTACT),
        CONNECTION(
                "rune_group.elementalcircuits.connection",
                Rune.Kind.FORCE,
                Rune.Kind.CONSTRAINT,
                Rune.Kind.INTERACT,
                Rune.Kind.ANCHOR,
                Rune.Kind.TARGET),
        SHAPE(
                "rune_group.elementalcircuits.shape",
                Rune.Kind.SPHERE,
                Rune.Kind.BEAM,
                Rune.Kind.SHIELD,
                Rune.Kind.FAN,
                Rune.Kind.WALL,
                Rune.Kind.RING,
                Rune.Kind.COLUMN,
                Rune.Kind.CROSS,
                Rune.Kind.DOME),
        EFFECT(
                "rune_group.elementalcircuits.effect",
                Rune.Kind.BURST,
                Rune.Kind.BOUNCE,
                Rune.Kind.HOMING,
                Rune.Kind.FRAGMENT,
                Rune.Kind.CHAIN,
                Rune.Kind.IGNITE,
                Rune.Kind.SLOW,
                Rune.Kind.KNOCKBACK),
        CUSTOMIZATION(
                "rune_group.elementalcircuits.customization",
                Rune.Kind.CUSTOM_EFFECT),
        PARAMETER(
                "rune_group.elementalcircuits.parameter",
                Rune.Kind.AMOUNT,
                Rune.Kind.RANGE,
                Rune.Kind.RADIUS,
                Rune.Kind.AIM,
                Rune.Kind.ANGLE,
                Rune.Kind.YAW,
                Rune.Kind.PITCH,
                Rune.Kind.DISTANCE),
        AUTOMATION(
                "rune_group.elementalcircuits.automation",
                Rune.Kind.ITEM_INPUT,
                Rune.Kind.ITEM_OUTPUT,
                Rune.Kind.REDSTONE,
                Rune.Kind.MATERIAL_FILTER);
        public final String titleKey;
        public final List<Rune.Kind> children;

        Group(String titleKey, Rune.Kind... children) {
            this.titleKey = titleKey;
            this.children = List.of(children);
        }

        public String title() {
            return UiText.string(titleKey);
        }
    }

    public record Row(Group group, Rune.Kind kind, Element element) {
        public boolean parent() {
            return kind == null;
        }

        public String title() {
            return element == null
                    ? RuneCatalog.title(kind)
                    : UiText.string("rune.elementalcircuits.convert", UiText.element(element));
        }
    }

    private RuneCatalog() {}

    public static List<Row> rows(Set<Group> expanded) {
        List<Row> rows = new ArrayList<>();
        for (Group group : Group.values()) {
            rows.add(new Row(group, null, null));
            if (expanded.contains(group)) {
                if (group == Group.ELEMENT)
                    for (Element element : Element.values())
                        rows.add(new Row(group, Rune.Kind.CONVERT, element));
                else for (Rune.Kind kind : group.children) rows.add(new Row(group, kind, null));
            }
        }
        return List.copyOf(rows);
    }

    public static String title(Rune.Kind k) {
        return UiText.string(
                "rune.elementalcircuits." + k.name().toLowerCase(Locale.ROOT) + ".title");
    }

    public static String description(Rune.Kind kind) {
        return UiText.string(
                "rune.elementalcircuits." + kind.name().toLowerCase(Locale.ROOT) + ".description");
    }
}
