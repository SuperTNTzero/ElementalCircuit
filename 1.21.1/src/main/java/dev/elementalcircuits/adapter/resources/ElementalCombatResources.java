package dev.elementalcircuits.adapter.resources;

import com.google.gson.*;

import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.combat.DamageAffinity;
import dev.elementalcircuits.domain.combat.ElementReactionRules;
import dev.elementalcircuits.domain.combat.ElementalCombatRules;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ElementalCombatResources {
    private static final ElementalCombatRules RULES = load();

    private ElementalCombatResources() {}

    public static ElementalCombatRules rules() {
        return RULES;
    }

    private static long n(JsonObject o, String key) {
        return o.get(key).getAsBigDecimal().longValueExact();
    }

    private static int i(JsonObject o, String key) {
        return Math.toIntExact(n(o, key));
    }

    private static ElementReactionRules reactions() {
        try (var stream =
                ElementalCombatResources.class.getResourceAsStream(
                        "/data/elementalcircuits/rules/element_reactions_v2.json")) {
            if (stream == null)
                throw new IllegalStateException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.resources._elemental_combat_resources.001"));
            var o =
                    JsonParser.parseString(
                                    new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                            .getAsJsonObject();
            var pairs = new ArrayList<ElementReactionRules.Counter>();
            for (var value : o.getAsJsonArray("counters")) {
                var a = value.getAsJsonArray();
                if (a.size() != 2)
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.adapter.resources._elemental_combat_resources.002"));
                pairs.add(
                        new ElementReactionRules.Counter(
                                a.get(0).getAsBigDecimal().intValueExact(),
                                a.get(1).getAsBigDecimal().intValueExact()));
            }
            return new ElementReactionRules(i(o, "version"), pairs);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ElementalCombatRules load() {
        try (var stream =
                ElementalCombatResources.class.getResourceAsStream(
                        "/data/elementalcircuits/rules/elemental_combat_v1.json")) {
            if (stream == null)
                throw new IllegalStateException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.resources._elemental_combat_resources.003"));
            var o =
                    JsonParser.parseString(
                                    new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                            .getAsJsonObject();
            var b = o.getAsJsonObject("baseDamage");
            var rates = new EnumMap<DamageAffinity, Long>(DamageAffinity.class);
            for (var a : DamageAffinity.values()) rates.put(a, n(b, a.name()));
            var f = o.getAsJsonObject("multipliers");
            var m =
                    new ElementalCombatRules.Multipliers(
                            n(f, "wetFire"),
                            n(f, "wetLightning"),
                            n(f, "wetFrost"),
                            n(f, "shatter"),
                            n(f, "undeadLight"));
            var s = o.getAsJsonObject("status");
            var status =
                    new ElementalCombatRules.Status(
                            i(s, "wetTicks"),
                            i(s, "freezePerMana"),
                            i(s, "thawPerMana"),
                            i(s, "maxFrozen"),
                            i(s, "freezeThreshold"),
                            i(s, "fireMaxSeconds"),
                            i(s, "fanTicks"),
                            i(s, "slowPerMana"),
                            i(s, "slowMaxTicks"),
                            i(s, "earthSlowPerMana"),
                            i(s, "earthSlowMaxTicks"),
                            i(s, "blindPerMana"),
                            i(s, "blindMaxTicks"),
                            n(s, "impulsePerMana"),
                            n(s, "maxImpulse"));
            return new ElementalCombatRules(
                    i(o, "version"),
                    rates,
                    n(o, "maxDamage"),
                    n(o, "threshold"),
                    m,
                    status,
                    reactions());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
