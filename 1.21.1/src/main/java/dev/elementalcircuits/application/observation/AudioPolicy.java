package dev.elementalcircuits.application.observation;

import java.util.*;

/** Versioned presentation limits, independent from spell costs and timing. */
public record AudioPolicy(
        int version,
        int range,
        int worldLimit,
        int playerLimit,
        int cellSize,
        Map<SpellSound.Kind, Integer> cooldowns,
        Map<SpellSound.Kind, Float> volumes) {
    public AudioPolicy {
        if (version != 1
                || range < 1
                || range > 64
                || worldLimit < 1
                || worldLimit > 256
                || playerLimit < 1
                || playerLimit > worldLimit
                || cellSize < 1
                || cellSize > 16)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.application.observation._audio_policy.001"));
        cooldowns = Map.copyOf(cooldowns);
        volumes = Map.copyOf(volumes);
        for (var k : SpellSound.Kind.values())
            if (!cooldowns.containsKey(k)
                    || !volumes.containsKey(k)
                    || cooldowns.get(k) < 1
                    || cooldowns.get(k) > 40
                    || !Float.isFinite(volumes.get(k))
                    || volumes.get(k) <= 0
                    || volumes.get(k) > 1)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.application.observation._audio_policy.002"));
    }

    public int maxCooldown() {
        return Collections.max(cooldowns.values());
    }
}
