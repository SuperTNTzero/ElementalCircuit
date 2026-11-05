package dev.elementalcircuits.adapter.resources;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.domain.casting.SecondaryRules;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class SecondaryResources {
    private static final SecondaryRules RULES = load();

    private SecondaryResources() {}

    private static SecondaryRules load() {
        try (InputStream in =
                SecondaryResources.class.getResourceAsStream(
                        "/data/elementalcircuits/rules/secondary_effects_v1.json")) {
            if (in == null)
                throw new IllegalStateException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.resources._secondary_resources.001"));
            return JsonCodecs.readSecondaryRules(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.resources._secondary_resources.002"),
                    e);
        }
    }

    public static SecondaryRules rules() {
        return RULES;
    }
}
