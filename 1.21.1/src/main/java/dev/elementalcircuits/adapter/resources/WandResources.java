package dev.elementalcircuits.adapter.resources;

import com.google.gson.*;

import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.equipment.WandParts;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class WandResources {
    private static final WandParts DEFINITIONS = load();

    private WandResources() {}

    public static WandParts definitions() {
        return DEFINITIONS;
    }

    private static long integer(JsonObject object, String key) {
        return object.get(key).getAsBigDecimal().longValueExact();
    }

    private static WandParts load() {
        try (InputStream in =
                WandResources.class.getResourceAsStream(
                        "/data/elementalcircuits/rules/wand_parts.json")) {
            if (in == null)
                throw new IllegalStateException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.resources._wand_resources.001"));
            JsonObject json =
                    JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                            .getAsJsonObject();
            List<WandParts.Part> parts = new ArrayList<>();
            for (JsonElement value : json.getAsJsonArray("parts")) {
                JsonObject p = value.getAsJsonObject();
                parts.add(
                        new WandParts.Part(
                                p.get("id").getAsString(),
                                WandParts.Slot.valueOf(p.get("slot").getAsString()),
                                p.get("title").getAsString(),
                                integer(p, "strength"),
                                integer(p, "speed"),
                                Math.toIntExact(integer(p, "durability")),
                                p.get("melee").getAsDouble(),
                                Integer.parseUnsignedInt(p.get("color").getAsString(), 16)));
            }
            return new WandParts(Math.toIntExact(integer(json, "version")), parts);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.resources._wand_resources.002"),
                    e);
        }
    }
}
