package dev.elementalcircuits.adapter.resources;

import com.google.gson.JsonParser;

import dev.elementalcircuits.domain.casting.CastCooldown;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class CooldownResources {
    private static final CastCooldown.Rules RULES = load();

    private CooldownResources() {}

    public static CastCooldown.Rules rules() {
        return RULES;
    }

    private static CastCooldown.Rules load() {
        try (InputStream in =
                CooldownResources.class.getResourceAsStream(
                        "/data/elementalcircuits/rules/wand_cooldown.json")) {
            if (in == null)
                throw new IllegalStateException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.resources._cooldown_resources.001"));
            var json =
                    JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                            .getAsJsonObject();
            return new CastCooldown.Rules(
                    json.get("version").getAsBigDecimal().intValueExact(),
                    json.get("base_ticks").getAsBigDecimal().intValueExact());
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.resources._cooldown_resources.002"),
                    e);
        }
    }
}
