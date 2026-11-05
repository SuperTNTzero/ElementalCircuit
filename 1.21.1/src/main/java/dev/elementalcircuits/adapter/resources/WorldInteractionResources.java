package dev.elementalcircuits.adapter.resources;

import com.google.gson.*;

import dev.elementalcircuits.domain.casting.WorldInteractionRules;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class WorldInteractionResources {
    private static final WorldInteractionRules RULES = load();

    private WorldInteractionResources() {}

    public static WorldInteractionRules rules() {
        return RULES;
    }

    private static long number(JsonObject o, String key) {
        return o.get(key).getAsBigDecimal().longValueExact();
    }

    private static WorldInteractionRules load() {
        try (InputStream in =
                WorldInteractionResources.class.getResourceAsStream(
                        "/data/elementalcircuits/rules/world_interactions_v1.json")) {
            if (in == null)
                throw new IllegalStateException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.resources._world_interaction_resources.001"));
            JsonObject o =
                    JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                            .getAsJsonObject();
            return new WorldInteractionRules(
                    Math.toIntExact(number(o, "version")),
                    number(o, "blockCost"),
                    number(o, "sourceCost"),
                    number(o, "impulsePerUnit"),
                    number(o, "maxImpulse"),
                    Math.toIntExact(number(o, "maxTargets")),
                    Math.toIntExact(number(o, "frostedIceDelay")));
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.resources._world_interaction_resources.002"),
                    e);
        }
    }
}
