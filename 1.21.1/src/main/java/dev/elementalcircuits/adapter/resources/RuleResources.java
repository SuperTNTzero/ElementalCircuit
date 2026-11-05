package dev.elementalcircuits.adapter.resources;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.domain.circuit.Rules;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class RuleResources {
    private static Rules defaults;

    private RuleResources() {}

    public static synchronized Rules defaults() {
        if (defaults == null)
            try (InputStream in =
                    RuleResources.class.getResourceAsStream(
                            "/data/elementalcircuits/rules/defaults.json")) {
                if (in == null)
                    throw new IllegalStateException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.adapter.resources._rule_resources.001"));
                defaults =
                        JsonCodecs.readRules(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.resources._rule_resources.002"),
                        e);
            }
        return defaults;
    }
}
