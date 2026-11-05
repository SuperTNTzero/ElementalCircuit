package dev.elementalcircuits.adapter.resources;

import com.google.gson.*;

import dev.elementalcircuits.domain.casting.RadianceRules;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class RadianceResources {
    private static final RadianceRules RULES = load();

    private RadianceResources() {}

    public static RadianceRules rules() {
        return RULES;
    }

    private static int number(JsonObject o, String key) {
        return o.get(key).getAsBigDecimal().intValueExact();
    }

    private static RadianceRules load() {
        try (InputStream in =
                RadianceResources.class.getResourceAsStream(
                        "/data/elementalcircuits/rules/radiance_v1.json")) {
            if (in == null)
                throw new IllegalStateException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.resources._radiance_resources.001"));
            JsonObject o =
                    JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                            .getAsJsonObject();
            return new RadianceRules(
                    number(o, "version"),
                    number(o, "lightLife"),
                    number(o, "shadowLife"),
                    number(o, "trailLife"),
                    number(o, "trailCost"),
                    number(o, "maxLights"),
                    number(o, "orphanDelay"));
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.resources._radiance_resources.002"),
                    e);
        }
    }
}
