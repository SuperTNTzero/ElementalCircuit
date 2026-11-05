package dev.elementalcircuits.adapter.resources;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.domain.circuit.AssemblyRules;

import java.nio.charset.StandardCharsets;

public final class AssemblyResources {
    private static final AssemblyRules RULES = load();

    private AssemblyResources() {}

    public static AssemblyRules rules() {
        return RULES;
    }

    private static AssemblyRules load() {
        try (var stream =
                AssemblyResources.class.getResourceAsStream(
                        "/data/elementalcircuits/rules/assembly_v1.json")) {
            if (stream == null)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.resources._assembly_resources.001"));
            return JsonCodecs.readAssemblyRules(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
