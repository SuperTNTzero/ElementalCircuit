package dev.elementalcircuits.adapter.resources;

import com.google.gson.JsonParser;

import dev.elementalcircuits.domain.combat.ElementalCombos;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class ElementalComboResources {
    private static final ElementalCombos.Rules RULES = load();

    private ElementalComboResources() {}

    public static ElementalCombos.Rules rules() {
        return RULES;
    }

    private static ElementalCombos.Rules load() {
        try (var in =
                ElementalComboResources.class.getResourceAsStream(
                        "/data/elementalcircuits/rules/elemental_combos_v1.json")) {
            if (in == null)
                throw new IllegalStateException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.resources._elemental_combo_resources.001"));
            var o =
                    JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                            .getAsJsonObject();
            return new ElementalCombos.Rules(
                    o.get("version").getAsBigDecimal().intValueExact(),
                    o.get("lightningJumps").getAsBigDecimal().intValueExact(),
                    o.get("lightningReach").getAsBigDecimal().intValueExact(),
                    o.get("lightningRetention").getAsBigDecimal().longValueExact());
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.resources._elemental_combo_resources.002"),
                    e);
        }
    }
}
