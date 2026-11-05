package dev.elementalcircuits.adapter.resources;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.domain.logic.LogicDefaults;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class LogicResources {
    private static final LogicDefaults DEFAULTS = load();

    private LogicResources() {}

    private static LogicDefaults load() {
        try (InputStream in =
                LogicResources.class.getResourceAsStream(
                        "/data/elementalcircuits/rules/logic_defaults_v1.json")) {
            if (in == null)
                throw new IllegalStateException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.resources._logic_resources.001"));
            return JsonCodecs.readLogicDefaults(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.resources._logic_resources.002"),
                    e);
        }
    }

    public static LogicDefaults defaults() {
        return DEFAULTS;
    }
}
