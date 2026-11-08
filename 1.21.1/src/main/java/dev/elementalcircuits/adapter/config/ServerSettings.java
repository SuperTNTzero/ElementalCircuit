package dev.elementalcircuits.adapter.config;

import dev.elementalcircuits.adapter.resources.ManaFieldResources;
import dev.elementalcircuits.adapter.resources.ManaGrowthResources;
import dev.elementalcircuits.adapter.resources.ManaRecoveryResources;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.CastingRules;
import dev.elementalcircuits.domain.casting.RecursionRules;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.BoardSafety;
import dev.elementalcircuits.domain.circuit.CircuitBounds;
import dev.elementalcircuits.domain.circuit.LayerRules;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.StandardState;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.ManaField;
import dev.elementalcircuits.domain.mana.ManaFieldRules;
import dev.elementalcircuits.domain.mana.ManaGrowthRules;
import dev.elementalcircuits.domain.mana.ManaRecoveryRules;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.*;

/**
 * Server policy is applied at creation/admission boundaries, never by domain constructors on
 * restore.
 */
public final class ServerSettings {
    public record Setting(
            double initial,
            double min,
            double max,
            boolean integer,
            String description,
            ModConfigSpec.ConfigValue<? extends Number> value) {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final Map<String, Setting> REGISTERED = new LinkedHashMap<>();
    public static final Map<String, Setting> VALUES = Collections.unmodifiableMap(REGISTERED);
    public static final ModConfigSpec SPEC;

    static {
        add(
                "inscription.requireRunes",
                1,
                0,
                1,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "configuration.elementalcircuits.require_runes.description"));
        add(
                "contact.enabled",
                1,
                0,
                1,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.001"));
        add(
                "contact.maxLinks",
                128,
                1,
                4096,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.002"));
        add(
                "contact.perOwner",
                16,
                1,
                256,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.003"));
        add(
                "contact.range",
                64,
                1,
                256,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.004"));
        add(
                "contact.maintenance",
                .02,
                .0000152587890625,
                1024,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.005"));
        add(
                "contact.distanceCost",
                .0001,
                0,
                1024,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.006"));
        add(
                "contact.loadCost",
                .005,
                0,
                1024,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.007"));
        add(
                "contact.workCost",
                .25,
                .0000152587890625,
                1024,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.008"));
        add(
                "contact.interactionCost",
                1,
                .0000152587890625,
                1024,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.009"));
        add(
                "contact.maxImpulse",
                .15,
                .001,
                1,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.010"));
        add(
                "contact.maxSpeed",
                .8,
                .01,
                4,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.011"));
        add(
                "automation.enabled",
                1,
                0,
                1,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.012"));
        add(
                "automation.breakBase",
                1,
                0.0000152587890625,
                1024,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.013"));
        add(
                "automation.hardnessCost",
                1,
                0,
                1024,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.014"));
        add(
                "automation.placeCost",
                1,
                0.0000152587890625,
                1024,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.015"));
        add(
                "automation.maxHardness",
                50,
                0,
                1000000,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.016"));
        add(
                "automation.maxContainerSlots",
                128,
                1,
                4096,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.017"));
        add(
                "enchant.guardPerLevel",
                0.04,
                0,
                0.2,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.018"));
        add(
                "enchant.guardMaximum",
                0.8,
                0,
                0.95,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.019"));
        add(
                "enchant.boardReductionPerLevel",
                0.15,
                0,
                0.3,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.020"));
        add(
                "enchant.speedPerLevel",
                0.1,
                0,
                1,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.021"));
        add(
                "enchant.repairPerLevel",
                1,
                1,
                16,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.022"));
        add(
                "enchant.repairManaPerPoint",
                4,
                0.0000152587890625,
                1024,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.023"));

        add(
                "ecology.enabled",
                1,
                0,
                1,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.024"));
        add(
                "ecology.nodeRadius",
                24,
                1,
                64,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.025"));
        add(
                "ecology.conversionField",
                24,
                1,
                65536,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.026"));
        add(
                "ecology.conversionTicks",
                600,
                20,
                72000,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.027"));
        add(
                "ecology.gelRestore",
                16,
                1,
                65536,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.028"));
        add(
                "ecology.potionRestore",
                32,
                1,
                65536,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.029"));
        add(
                "field.aerialCapacity",
                1024,
                1,
                65536,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.030"));
        add(
                "field.aerialPerStep",
                1,
                0.0000152587890625,
                65536,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.031"));
        add(
                "field.aerialRegeneration",
                2,
                0,
                65536,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.032"));

        for (var row : new int[][] {{128, 256}, {512, 1024}, {1280, 4096}}) {
            String material = row[0] == 128 ? "wood" : row[0] == 512 ? "brass" : "crystal";
            add(
                    "board." + material + "Capacity",
                    row[0],
                    1,
                    65536,
                    false,
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.config._server_settings.033"));
            add(
                    "board." + material + "Durability",
                    row[1],
                    1,
                    1000000,
                    true,
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.config._server_settings.034"));
        }
        add(
                "board.fuseMultiplier",
                4,
                2,
                64,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.035"));
        add(
                "board.inputWear",
                .125,
                0,
                64,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.036"));
        add(
                "board.overloadWear",
                8,
                0,
                1024,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.037"));
        add(
                "board.wandWearScale",
                .05,
                0,
                1,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.038"));
        add(
                "board.blastRadius",
                2,
                .25,
                8,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.039"));
        add(
                "board.blastDamage",
                6,
                0,
                256,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.040"));
        add(
                "board.maximumBlastRadius",
                8,
                .5,
                16,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.041"));
        add(
                "board.maximumBlastDamage",
                80,
                1,
                1024,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.042"));
        add(
                "board.breakBlocks",
                1,
                0,
                1,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.043"));
        for (var e : Element.values())
            add(
                    "board.volatility" + e.name(),
                    switch (e) {
                        case PURE, WATER -> .6;
                        case FIRE -> 1.5;
                        case LIGHTNING -> 1.4;
                        case WIND, LIGHT -> .8;
                        case SHADOW -> 1.1;
                        case ARCANE -> 1.25;
                        default -> 1;
                    },
                    .05,
                    8,
                    false,
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.config._server_settings.044"));
        add(
                "input.holdTicks",
                6,
                2,
                20,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.045"));
        add(
                "input.fullChargeTicks",
                40,
                21,
                200,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.046"));
        add(
                "input.maximumCharge",
                2,
                1,
                2,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.047"));
        add(
                "crystal.nodeEmissionMultiplier",
                1.5,
                1,
                4,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.048"));
        add(
                "crystal.naturalRecoveryMultiplier",
                1.5,
                1,
                4,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.049"));
        add(
                "crystal.circuitPowerMultiplier",
                1.25,
                1,
                4,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.050"));
        add(
                "crystal.circuitSpeedMultiplier",
                1.25,
                1,
                2,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.051"));
        add(
                "player.percentRegeneration",
                0.002,
                0,
                0.1,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.052"));
        add(
                "standard.transmissionLossScale",
                0.1,
                0,
                1,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.053"));
        add(
                "standard.movingLossScale",
                0.05,
                0,
                1,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.054"));
        add(
                "standard.waitingLossScale",
                0.1,
                0,
                1,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.055"));
        add(
                "standard.storageLossPerTick",
                0.00005,
                0,
                1,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.056"));
        add(
                "standard.range",
                12,
                1,
                CircuitBounds.RANGE,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.057"));
        add(
                "standard.radius",
                2,
                1,
                CircuitBounds.RADIUS,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.058"));
        add(
                "standard.aim",
                0.125,
                0,
                1,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.059"));
        add(
                "standard.delayTicks",
                2,
                0,
                1200,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.060"));
        add(
                "standard.maximumDelayTicks",
                1200,
                0,
                1200,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.061"));
        add(
                "standard.nodeWeight",
                1,
                0,
                16,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.062"));
        add(
                "standard.lengthWeight",
                0.1,
                0,
                16,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.063"));
        add(
                "standard.depthWeight",
                0.5,
                0,
                16,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.064"));
        add(
                "standard.feedbackWeight",
                2,
                0,
                16,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.065"));
        add(
                "casting.parameterLossScale",
                0.01,
                0,
                1,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.066"));
        add(
                "casting.shapeCostPerBlock",
                0.002,
                0,
                1,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.067"));
        add(
                "casting.failureLossScale",
                1,
                0,
                4,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.068"));
        add(
                "casting.movingLossPerTick",
                0.002,
                0,
                1,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.069"));
        add(
                "casting.cacheLossPerTick",
                0.05,
                0,
                1,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.070"));
        add(
                "casting.refundClaimBudget",
                65536,
                1024,
                1048576,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.071"));
        add(
                "casting.refundWorkPerTick",
                128,
                1,
                4096,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.072"));
        add(
                "casting.defaultEffect",
                1,
                0.0000152587890625,
                65536,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.073"));
        add(
                "player.spellPowerCostExponent",
                1,
                0,
                4,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.074"));
        for (var entry :
                Map.ofEntries(
                                Map.entry("pure", .9),
                                Map.entry("earth", .85),
                                Map.entry("water", .9),
                                Map.entry("fire", .6),
                                Map.entry("wind", .8),
                                Map.entry("lightning", .5),
                                Map.entry("light", .9),
                                Map.entry("shadow", .7),
                                Map.entry("arcane", .75),
                                Map.entry("frost", .8))
                        .entrySet())
            add(
                    "refund." + entry.getKey(),
                    entry.getValue(),
                    0,
                    1,
                    false,
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.config._server_settings.075"));
        add(
                "field.naturalRegeneration",
                1,
                0,
                65536,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.076"));
        add(
                "field.intakeReference",
                8,
                0.0000152587890625,
                65536,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.077"));
        add(
                "wear.intensityCoefficient",
                1,
                0,
                64,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.078"));
        add(
                "wear.speedCoefficient",
                1,
                0,
                64,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.079"));
        for (var head : List.of("quartz", "amethyst", "copper")) {
            add(
                    "wear." + head + "Intensity",
                    head.equals("amethyst") ? 32 : head.equals("copper") ? 12 : 16,
                    1,
                    65536,
                    false,
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.config._server_settings.080"));
            add(
                    "wear." + head + "Frequency",
                    head.equals("amethyst") ? 1.5 : head.equals("copper") ? 3 : 2,
                    .1,
                    20,
                    false,
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.config._server_settings.081"));
        }
        add(
                "player.initialCapacity",
                64,
                1,
                65536,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.082"));
        add(
                "manaPotion.bonusPerLevel",
                1,
                0,
                16,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.083"));
        add(
                "player.regeneration",
                4,
                0,
                65536,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.084"));
        add(
                "player.recoveryDelayTicks",
                40,
                0,
                2147483646,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.085"));
        add(
                "player.fruitIncrease",
                16,
                1,
                65536,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.086"));
        add(
                "player.fruitMaximum",
                1024,
                1,
                65536,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.087"));
        add(
                "circuit.woodRadius",
                4,
                1,
                CircuitBounds.BOARD,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.088"));
        add(
                "circuit.brassRadius",
                6,
                1,
                CircuitBounds.BOARD,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.089"));
        add(
                "circuit.crystalRadius",
                8,
                1,
                CircuitBounds.BOARD,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.090"));
        add(
                "circuit.maxRunes",
                4096,
                1,
                CircuitBounds.RUNES,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.091"));
        add(
                "circuit.maxRoutes",
                8192,
                1,
                CircuitBounds.ROUTES,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.092"));
        add(
                "circuit.maxPath",
                4096,
                2,
                CircuitBounds.PATH,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.093"));
        add(
                "circuit.maxDelayTicks",
                65536,
                1,
                CircuitBounds.DELAY_TICKS,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.094"));
        add(
                "probe.range",
                16,
                1,
                64,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.095"));
        add(
                "circuit.manaSteps",
                8,
                1,
                64,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.096"));
        add(
                "circuit.nodeCapacity",
                1024,
                1,
                65536,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.097"));
        add(
                "circuit.defaultInjection",
                4,
                0.0000152587890625,
                65536,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.098"));
        add(
                "circuit.injectionInterval",
                1,
                1,
                2147483646,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.099"));
        add(
                "circuit.singleWindow",
                120,
                1,
                2147483646,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.100"));
        add(
                "spell.maxRange",
                256,
                1,
                CircuitBounds.RANGE,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.101"));
        add(
                "spell.maxRadius",
                16,
                1,
                CircuitBounds.RADIUS,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.102"));
        add(
                "spell.maxDerivatives",
                32,
                2,
                CircuitBounds.DERIVATIVES,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.103"));
        add(
                "spell.maxDurationSeconds",
                3600,
                1,
                CircuitBounds.DURATION,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.104"));
        add(
                "spell.maxProjectiles",
                1024,
                1,
                4096,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.105"));
        add(
                "spell.maxTargets",
                128,
                1,
                4096,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.106"));
        add(
                "spell.blockScanBudget",
                16384,
                1,
                262144,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.107"));
        add(
                "layers.initialDepth",
                1,
                1,
                16,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.108"));
        add(
                "layers.depthGrowth",
                2,
                1,
                4,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.109"));
        add(
                "recursion.maxDepth",
                4,
                1,
                16,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.110"));
        add(
                "recursion.activations",
                64,
                1,
                1024,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.111"));
        add(
                "recursion.jobTicks",
                120,
                1,
                1200,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.112"));
        add(
                "recursion.retention",
                0.95,
                0.0000152587890625,
                1,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.113"));
        add(
                "recursion.maxChains",
                128,
                1,
                1024,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.114"));
        add(
                "recursion.maxJobs",
                128,
                1,
                1024,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.115"));
        add(
                "recursion.activeUnits",
                16384,
                1,
                65536,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.116"));
        add(
                "recursion.workPerTick",
                32,
                1,
                1024,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.117"));
        add(
                "probe.radius",
                500,
                1,
                2048,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.118"));
        add(
                "probe.chunksPerTick",
                16,
                1,
                1024,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.119"));
        add(
                "probe.maxConcurrent",
                2,
                1,
                8,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.120"));
        add(
                "probe.readAhead",
                16,
                1,
                16,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.121"));
        add(
                "probe.timeoutTicks",
                2400,
                20,
                72000,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.122"));
        add(
                "field.enabled",
                1,
                0,
                1,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.123"));
        add(
                "field.cellCapacity",
                128,
                1,
                65536,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.124"));
        add(
                "field.ambient",
                8,
                0,
                65536,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.125"));
        add(
                "field.stepTicks",
                5,
                1,
                20,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.126"));
        add(
                "field.diffusionDivisor",
                12,
                6,
                1024,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.127"));
        add(
                "field.decayDivisor",
                4096,
                64,
                65536,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.128"));
        add(
                "field.intakePerTick",
                0.1999969482421875,
                0.0000152587890625,
                65536,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.129"));
        add(
                "field.crystalCharge",
                64,
                0.0000152587890625,
                65536,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.130"));
        add(
                "field.naturalCapacity",
                4096,
                1,
                65536,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.131"));
        add(
                "field.artificialCapacity",
                512,
                1,
                65536,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.132"));
        add(
                "field.naturalPerStep",
                0.5,
                0.0000152587890625,
                65536,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.133"));
        add(
                "field.artificialPerStep",
                1,
                0.0000152587890625,
                65536,
                false,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.134"));
        add(
                "field.activeCellBudget",
                8192,
                1,
                ManaField.MAX_ACTIVE,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.135"));
        add(
                "field.storedCellBudget",
                262144,
                1,
                ManaField.MAX_CELLS,
                true,
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.adapter.config._server_settings.136"));
        SPEC = BUILDER.build();
    }

    private static void add(
            String key,
            double initial,
            double min,
            double max,
            boolean integer,
            String description) {
        BUILDER.comment(description).translation("config.elementalcircuits." + key);
        ModConfigSpec.ConfigValue<? extends Number> value =
                integer
                        ? BUILDER.defineInRange(key, (int) initial, (int) min, (int) max)
                        : BUILDER.defineInRange(key, initial, min, max);
        REGISTERED.put(key, new Setting(initial, min, max, integer, description, value));
    }

    private static void assign(Setting s, double n) {
        if (s.integer()) ((ModConfigSpec.IntValue) s.value()).set((int) n);
        else ((ModConfigSpec.DoubleValue) s.value()).set(n);
    }

    public static double get(String key) {
        var s = VALUES.get(key);
        double n = SPEC.isLoaded() ? s.value().get().doubleValue() : s.initial();
        if (!Double.isFinite(n) || s.integer() && n != Math.rint(n)) return s.initial();
        return n;
    }

    public static int count(String key) {
        return (int) get(key);
    }

    public static long mana(String key) {
        return Math.round(get(key) * Fixed.ONE);
    }

    public static BoardSafety boardSafety() {
        var profiles = new EnumMap<Rules.Material, BoardSafety.Profile>(Rules.Material.class);
        for (var m : Rules.Material.values()) {
            String key = m.name().toLowerCase(Locale.ROOT);
            profiles.put(
                    m,
                    new BoardSafety.Profile(
                            mana("board." + key + "Capacity"),
                            count("board." + key + "Durability")));
        }
        var volatility = new ArrayList<Double>();
        volatility.add(get("board.volatilityPURE"));
        for (var e : Element.values())
            if (e != Element.PURE)
                for (int phase = 0; phase < 2; phase++)
                    volatility.add(get("board.volatility" + e.name()));
        return new BoardSafety(
                1,
                profiles,
                get("board.fuseMultiplier"),
                get("board.inputWear"),
                get("board.overloadWear"),
                get("board.blastRadius"),
                get("board.blastDamage"),
                get("board.maximumBlastRadius"),
                get("board.maximumBlastDamage"),
                volatility);
    }

    public static void set(String key, double n) {
        var s = VALUES.get(key);
        if (s == null
                || !Double.isFinite(n)
                || n < s.min()
                || n > s.max()
                || s.integer() && n != Math.rint(n))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.config._server_settings.137"));
        double old = s.value().get().doubleValue();
        assign(s, n);
        try {
            buildCircuitRules();
            buildFieldRules();
            buildGrowthRules();
            SPEC.save();
        } catch (RuntimeException e) {
            assign(s, old);
            throw e;
        }
    }

    private static Rules buildCircuitRules() {
        var r = RuleResources.defaults();
        return new Rules(
                4,
                r.material(),
                r.conversion(),
                r.inversion(),
                r.coupling(),
                mana("circuit.nodeCapacity"),
                mana("circuit.defaultInjection"),
                count("circuit.injectionInterval"),
                count("circuit.singleWindow"),
                r.signalCost(),
                mana("player.initialCapacity"),
                mana("player.regeneration"),
                count("player.recoveryDelayTicks"),
                r.range(),
                r.radius(),
                r.routingRetention(),
                r.oppositionRetention(),
                new Rules.BoardSizes(
                        count("circuit.woodRadius"),
                        count("circuit.brassRadius"),
                        count("circuit.crystalRadius")),
                count("circuit.manaSteps"),
                castingRules());
    }

    public static CastingRules castingRules() {
        var rates = new ArrayList<Long>();
        rates.add(mana("refund.pure"));
        for (var element : Element.values())
            if (element != Element.PURE)
                for (int phase = 0; phase < 2; phase++)
                    rates.add(
                            mana(
                                    "refund."
                                            + (element == Element.FIRE && phase == 1
                                                    ? "frost"
                                                    : element.name()
                                                            .toLowerCase(java.util.Locale.ROOT))));
        return new CastingRules(
                2,
                mana("casting.parameterLossScale"),
                mana("casting.shapeCostPerBlock"),
                mana("casting.failureLossScale"),
                rates,
                Fixed.mul(mana("casting.movingLossPerTick"), mana("standard.movingLossScale")),
                Fixed.mul(mana("casting.cacheLossPerTick"), mana("standard.waitingLossScale")),
                mana("casting.defaultEffect"),
                standardState());
    }

    public static StandardState standardState() {
        return new StandardState(
                1,
                mana("standard.transmissionLossScale"),
                mana("standard.storageLossPerTick"),
                count("standard.delayTicks"),
                count("standard.maximumDelayTicks"),
                mana("standard.nodeWeight"),
                mana("standard.lengthWeight"),
                mana("standard.depthWeight"),
                mana("standard.feedbackWeight"),
                Math.max(
                        1,
                        5 * mana("standard.nodeWeight")
                                + 4 * mana("standard.lengthWeight")
                                + 4 * mana("standard.depthWeight")),
                count("standard.range"),
                count("standard.radius"),
                (int) mana("standard.aim"));
    }

    private static ManaGrowthRules buildGrowthRules() {
        return new ManaGrowthRules(2, mana("player.fruitIncrease"), mana("player.fruitMaximum"));
    }

    private static ManaFieldRules buildFieldRules() {
        return new ManaFieldRules(
                1,
                8,
                count("field.stepTicks"),
                mana("field.cellCapacity"),
                mana("field.ambient"),
                count("field.diffusionDivisor"),
                count("field.decayDivisor"),
                mana("field.intakePerTick"),
                mana("field.crystalCharge"),
                mana("field.naturalCapacity"),
                mana("field.artificialCapacity"),
                mana("field.naturalPerStep"),
                mana("field.artificialPerStep"));
    }

    private static final java.util.Set<String> WARNED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void invalid(String group, RuntimeException e) {
        if (WARNED.add(group + e.getMessage()))
            org.slf4j.LoggerFactory.getLogger("ElementalCircuits")
                    .error(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.adapter.config._server_settings.138"),
                            group,
                            e.getMessage());
    }

    public static LayerRules layerRules() {
        return new LayerRules(count("layers.initialDepth"), mana("layers.depthGrowth"));
    }

    public static RecursionRules recursionRules() {
        return new RecursionRules(
                1,
                count("recursion.maxDepth"),
                count("recursion.activations"),
                count("recursion.jobTicks"),
                mana("recursion.retention"));
    }

    public static Rules circuitRules() {
        try {
            return buildCircuitRules();
        } catch (RuntimeException e) {
            invalid(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.config._server_settings.139"),
                    e);
            return RuleResources.defaults();
        }
    }

    public static ManaFieldRules checkedFieldRules() {
        return buildFieldRules();
    }

    public static ManaFieldRules fieldRules() {
        try {
            return buildFieldRules();
        } catch (RuntimeException e) {
            invalid(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.config._server_settings.140"),
                    e);
            return ManaFieldResources.defaults();
        }
    }

    public static ManaRecoveryRules recoveryRules() {
        return ManaRecoveryResources.rules().withBonus(mana("manaPotion.bonusPerLevel"));
    }

    public static ManaGrowthRules growthRules() {
        try {
            return buildGrowthRules();
        } catch (RuntimeException e) {
            invalid(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.config._server_settings.141"),
                    e);
            return ManaGrowthResources.rules();
        }
    }

    public static void admit(Blueprint b) {
        if (b.runes().size() > count("circuit.maxRunes")
                || b.routes().size() > count("circuit.maxRoutes")
                || b.routes().stream().anyMatch(r -> r.path().size() > count("circuit.maxPath")))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.config._server_settings.142"));
        for (var r : b.runes()) {
            long v = r.value() / Fixed.ONE;
            int max =
                    switch (r.kind()) {
                        case RANGE, HOMING -> count("spell.maxRange");
                        case DELAY -> count("circuit.maxDelayTicks");
                        case RADIUS, BURST -> count("spell.maxRadius");
                        case FRAGMENT, CHAIN, BOUNCE, KNOCKBACK -> count("spell.maxDerivatives");
                        case IGNITE, SLOW -> count("spell.maxDurationSeconds");
                        default -> Integer.MAX_VALUE;
                    };
            if (v > max)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.adapter.config._server_settings.143")
                                + r.kind()
                                + dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.adapter.config._server_settings.144")
                                + max);
        }
    }

    private ServerSettings() {}
}
