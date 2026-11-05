package dev.elementalcircuits.adapter.resources;

import com.google.gson.*;

import dev.elementalcircuits.application.observation.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class AudioResources {
    private static final AudioPolicy POLICY = load();

    private AudioResources() {}

    public static AudioPolicy policy() {
        return POLICY;
    }

    private static AudioPolicy load() {
        try (var input =
                        Objects.requireNonNull(
                                AudioResources.class.getResourceAsStream(
                                        "/assets/elementalcircuits/audio/policy.json"));
                var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            var j = JsonParser.parseReader(reader).getAsJsonObject();
            var cooldowns = new EnumMap<SpellSound.Kind, Integer>(SpellSound.Kind.class);
            var volumes = new EnumMap<SpellSound.Kind, Float>(SpellSound.Kind.class);
            for (var k : SpellSound.Kind.values()) {
                var style = j.getAsJsonObject("events").getAsJsonObject(k.name());
                cooldowns.put(k, integer(style, "cooldown_ticks"));
                volumes.put(k, style.get("volume").getAsFloat());
            }
            return new AudioPolicy(
                    integer(j, "version"),
                    integer(j, "range"),
                    integer(j, "world_limit"),
                    integer(j, "player_limit"),
                    integer(j, "cell_size"),
                    cooldowns,
                    volumes);
        } catch (IOException e) {
            throw new IllegalStateException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.resources._audio_resources.001"),
                    e);
        }
    }

    private static int integer(JsonObject j, String key) {
        return j.get(key).getAsBigDecimal().intValueExact();
    }
}
