package dev.elementalcircuits.server.equipment;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.CastContext;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.*;

/**
 * Native, synchronized player attribute. A release freezes both multipliers into its paid
 * invocation.
 */
public final class SpellAttributes {
    private static final DeferredRegister<Attribute> TYPES =
            DeferredRegister.create(Registries.ATTRIBUTE, ElementalCircuits.ID);
    public static final DeferredHolder<Attribute, Attribute> POWER =
            TYPES.register(
                    "spell_power",
                    () ->
                            new FiniteAttribute("attribute.elementalcircuits.spell_power", 1, 1, 16)
                                    .setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> MANA_CAPACITY =
            TYPES.register(
                    "mana_capacity",
                    () ->
                            new FiniteAttribute(
                                            "attribute.elementalcircuits.mana_capacity",
                                            0,
                                            0,
                                            65536)
                                    .setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> MANA_REGENERATION =
            TYPES.register(
                    "mana_regeneration",
                    () ->
                            new FiniteAttribute(
                                            "attribute.elementalcircuits.mana_regeneration",
                                            0,
                                            0,
                                            65536)
                                    .setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> MANA_COST =
            TYPES.register(
                    "mana_cost",
                    () ->
                            new FiniteAttribute(
                                            "attribute.elementalcircuits.mana_cost",
                                            1,
                                            1.0 / 65536,
                                            16)
                                    .setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> MANA_REDUCTION =
            TYPES.register(
                    "mana_cost_reduction",
                    () ->
                            new FiniteAttribute(
                                            "attribute.elementalcircuits.mana_cost_reduction",
                                            0,
                                            0,
                                            80)
                                    .setSyncable(true));

    /** Vanilla maps NaN to the lower bound, which would turn cost corruption into a discount. */
    private static final class FiniteAttribute extends RangedAttribute {
        private FiniteAttribute(String key, double initial, double min, double max) {
            super(key, initial, min, max);
        }

        @Override
        public double sanitizeValue(double value) {
            return Double.isFinite(value) ? super.sanitizeValue(value) : getDefaultValue();
        }
    }

    private SpellAttributes() {}

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        bus.addListener(SpellAttributes::attributes);
    }

    private static void attributes(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, POWER);
        event.add(EntityType.PLAYER, MANA_COST);
        event.add(EntityType.PLAYER, MANA_REDUCTION);
        event.add(EntityType.PLAYER, MANA_CAPACITY);
        event.add(EntityType.PLAYER, MANA_REGENERATION);
    }

    public static double value(
            ServerPlayer player, net.minecraft.core.Holder<Attribute> attribute) {
        double value = player.getAttributeValue(attribute);
        return Double.isFinite(value) ? value : attribute.value().getDefaultValue();
    }

    public static CastContext context(ServerPlayer player) {
        double p = value(player, POWER);
        long power = Math.round(p * Fixed.ONE);
        long cost =
                Math.clamp(
                        Math.round(
                                Math.pow(p, ServerSettings.get("player.spellPowerCostExponent"))
                                        * value(player, MANA_COST)
                                        * (1 - value(player, MANA_REDUCTION) / 100)
                                        * Fixed.ONE),
                        1,
                        Fixed.MAX);
        return new CastContext(power, cost);
    }
}
