package dev.elementalcircuits.domain.circuit;

import dev.elementalcircuits.domain.casting.RecursionRules;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.casting.TriggerBinding;

import java.util.*;

/** Shared immutable definitions. V1 addresses physical slots; V2 addresses layers on one plate. */
public record InvocationProgram(
        int version,
        List<Definition> definitions,
        List<TriggerBinding> bindings,
        RecursionRules rules) {
    public record Definition(int slot, Blueprint blueprint) {
        public Definition {
            if (slot < 1 || slot > 65534 || blueprint == null)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._invocation_program.001"));
        }
    }

    public InvocationProgram {
        if ((version < 1 || version > 2)
                || definitions == null
                || bindings == null
                || rules == null
                || definitions.isEmpty()
                || definitions.size() > (version == 1 ? 4 : 17)
                || bindings.size() > (version == 1 ? 4 : 17))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._invocation_program.002"));
        definitions = List.copyOf(definitions);
        bindings = List.copyOf(bindings);
        Set<Integer> ids = new HashSet<>(), sources = new HashSet<>();
        for (var d : definitions)
            if (!ids.add(d.slot()))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._invocation_program.003"));
        if (version == 2) {
            var root =
                    definitions.stream()
                            .filter(d -> d.slot() == 1)
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    dev.elementalcircuits.text.Texts.get(
                                                            "text.elementalcircuits.domain.circuit._invocation_program.004")));
            for (var d : definitions)
                if (!d.blueprint().rules().equals(root.blueprint().rules()))
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.domain.circuit._invocation_program.005"));
        }
        for (var b : bindings) {
            if (!sources.add(b.source()) || !ids.contains(b.source()) || !ids.contains(b.target()))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._invocation_program.006"));
            if (definitions.stream()
                    .filter(d -> d.slot() == b.target())
                    .findFirst()
                    .orElseThrow()
                    .blueprint()
                    .runes()
                    .stream()
                    .noneMatch(r -> r.kind() == Rune.Kind.INPUT))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._invocation_program.007"));
        }
    }

    public Blueprint definition(int slot) {
        return definitions.stream()
                .filter(d -> d.slot() == slot)
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        dev.elementalcircuits.text.Texts.get(
                                                "text.elementalcircuits.domain.circuit._invocation_program.008")))
                .blueprint();
    }

    public TriggerBinding binding(int slot) {
        return bindings.stream().filter(b -> b.source() == slot).findFirst().orElse(null);
    }

    public static InvocationProgram freeze(CircuitAssembly a, RecursionRules rules) {
        return new InvocationProgram(
                1,
                a.slots().stream()
                        .map(id -> new Definition(id, a.circuit(id).definition()))
                        .toList(),
                a.triggers(),
                rules);
    }

    public static InvocationProgram freeze(
            CircuitAssembly a, int slot, RecursionRules rules, int materialDepth) {
        var layers = a.layers(slot);
        var limited =
                new RecursionRules(
                        rules.version(),
                        materialDepth,
                        rules.activations(),
                        rules.jobTicks(),
                        rules.retention());
        return new InvocationProgram(
                2,
                layers.ids().stream().map(id -> new Definition(id, layers.definition(id))).toList(),
                layers.state().bindings(),
                limited);
    }

    public static boolean projectile(SpellSpec spec) {
        return spec.method() == SpellSpec.Method.DIRECTED
                && (spec.shape() == SpellSpec.Shape.SPHERE
                        || spec.shape() == SpellSpec.Shape.UNSET);
    }
}
