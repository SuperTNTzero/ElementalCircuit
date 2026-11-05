package dev.elementalcircuits.adapter.resources;

import com.google.gson.JsonParser;

import dev.elementalcircuits.domain.mana.ManaGrowthRules;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class ManaGrowthResources {
    private static final ManaGrowthRules RULES = load();

    private ManaGrowthResources() {}

    public static ManaGrowthRules rules() {
        return RULES;
    }

    private static ManaGrowthRules load() {
        try (InputStream in =
                ManaGrowthResources.class.getResourceAsStream(
                        "/data/elementalcircuits/rules/mana_growth_v1.json")) {
            if (in == null)
                throw new IllegalStateException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.resources._mana_growth_resources.001"));
            var json =
                    JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                            .getAsJsonObject();
            return new ManaGrowthRules(
                    json.get("version").getAsBigDecimal().intValueExact(),
                    json.get("fruitIncrease").getAsBigDecimal().longValueExact(),
                    json.get("maximum").getAsBigDecimal().longValueExact());
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.resources._mana_growth_resources.002"),
                    e);
        }
    }
}
