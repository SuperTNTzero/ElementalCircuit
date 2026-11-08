package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.domain.circuit.*;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.casting.SpellSpec;

import java.util.*;

public final class RuneMaterialsContracts {
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Rune material accounting");
    }

    private static Blueprint graph(Rune... runes) {
        return new Blueprint(5, RuleResources.defaults(), List.of(runes), List.of(), false);
    }

    public static int run() {
        var input = Rune.create(1, Rune.Kind.INPUT, new Hex(0, 0));
        var second = Rune.create(2, Rune.Kind.INPUT, new Hex(1, 0));
        var empty = graph();
        check(RuneMaterials.additions(empty, graph(input)).equals(Map.of("rune_input", 1)));
        check(
                RuneMaterials.additions(graph(input), graph(input, second))
                        .equals(Map.of("rune_input", 1)));
        check(RuneMaterials.additions(graph(input), graph(second)).isEmpty());
        check(RuneMaterials.additions(graph(input), empty).isEmpty());
        check(RuneMaterials.additions(graph(input), graph(input)).isEmpty());
        check(
                !RuneMaterials.key(Rune.Kind.CONVERT, Element.FIRE)
                        .equals(RuneMaterials.key(Rune.Kind.CONVERT, Element.WATER)));
        check(
                RuneMaterials.key(Rune.Kind.INPUT, Element.FIRE)
                        .equals(RuneMaterials.key(Rune.Kind.INPUT, Element.WATER)));
        check(RuneMaterials.additions(empty, graph(input, second)).get("rune_input") == 2);
        var effect = Rune.create(3, Rune.Kind.CUSTOM_EFFECT, new Hex(2, 0));
        effect = new Rune(effect.id(), effect.kind(), effect.cell(), effect.element(), effect.value(),
                effect.on(), effect.signalControl(), effect.weights(), effect.storage(), "ember");
        check(RuneMaterials.key(effect.kind(), Element.PURE).equals("rune_custom_effect"));
        check(RuneMaterials.additions(empty, graph(effect)).equals(Map.of("rune_custom_effect", 1)));
        var selected = SpellSpec.EMPTY.modify(effect);
        check(selected.customEffectId().equals("ember"));
        check(selected.withDamageMultiplier(dev.elementalcircuits.domain.Fixed.ONE)
                .customEffectId().equals("ember"));
        boolean rejected = false;
        try {
            SpellSpec.EMPTY.modify(new Rune(4, Rune.Kind.CUSTOM_EFFECT, new Hex(3, 0),
                    Element.PURE, 0, true, false, List.of(1),
                    dev.elementalcircuits.domain.mana.StorageSettings.NONE, "../bad"));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected);
        return 13;
    }

    public static void main(String[] args) {
        System.out.println("Rune material contracts passed: " + run());
    }
}
