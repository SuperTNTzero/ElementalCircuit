package dev.elementalcircuits.adapter.resources;

import com.google.gson.JsonParser;

import dev.elementalcircuits.domain.mana.ManaRecoveryRules;

import java.nio.charset.StandardCharsets;

public final class ManaRecoveryResources {
    private static final ManaRecoveryRules RULES = load();

    private ManaRecoveryResources() {}

    public static ManaRecoveryRules rules() {
        return RULES;
    }

    private static ManaRecoveryRules load() {
        try (var stream =
                ManaRecoveryResources.class.getResourceAsStream(
                        "/data/elementalcircuits/rules/mana_recovery_v1.json")) {
            if (stream == null)
                throw new IllegalStateException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.resources._mana_recovery_resources.001"));
            var o =
                    JsonParser.parseString(
                                    new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                            .getAsJsonObject();
            return new ManaRecoveryRules(
                    o.get("version").getAsBigDecimal().intValueExact(),
                    o.get("bonusPerLevel").getAsBigDecimal().longValueExact(),
                    o.get("duration").getAsBigDecimal().intValueExact(),
                    o.get("longDuration").getAsBigDecimal().intValueExact(),
                    o.get("strongDuration").getAsBigDecimal().intValueExact());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
