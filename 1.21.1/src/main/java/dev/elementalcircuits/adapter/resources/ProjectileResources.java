package dev.elementalcircuits.adapter.resources;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.domain.casting.ProjectileRules;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class ProjectileResources {
    private static final ProjectileRules RULES = load();

    private ProjectileResources() {}

    private static ProjectileRules load() {
        try (InputStream in =
                ProjectileResources.class.getResourceAsStream(
                        "/data/elementalcircuits/rules/projectile_effects_v1.json")) {
            if (in == null)
                throw new IllegalStateException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.resources._projectile_resources.001"));
            return JsonCodecs.readProjectileRules(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.resources._projectile_resources.002"),
                    e);
        }
    }

    public static ProjectileRules rules() {
        return RULES;
    }
}
