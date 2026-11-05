package dev.elementalcircuits.server.mana;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.resources.ManaRecoveryResources;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.registries.*;

import java.util.*;

/** Native potions own duration, milk removal, splash dilution, stacking and persistence. */
public final class ManaBrewing {
    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, ElementalCircuits.ID);
    private static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, ElementalCircuits.ID);
    public static final DeferredHolder<MobEffect, MobEffect> RECOVERY =
            EFFECTS.register("mana_recovery", RecoveryEffect::new);
    public static final DeferredHolder<Potion, Potion> MANA =
            POTIONS.register(
                    "mana_recovery",
                    () ->
                            new Potion(
                                    "mana_recovery",
                                    new MobEffectInstance(
                                            RECOVERY,
                                            ManaRecoveryResources.rules().duration(),
                                            0)));
    public static final DeferredHolder<Potion, Potion> LONG_MANA =
            POTIONS.register(
                    "long_mana_recovery",
                    () ->
                            new Potion(
                                    "mana_recovery",
                                    new MobEffectInstance(
                                            RECOVERY,
                                            ManaRecoveryResources.rules().longDuration(),
                                            0)));
    public static final DeferredHolder<Potion, Potion> STRONG_MANA =
            POTIONS.register(
                    "strong_mana_recovery",
                    () ->
                            new Potion(
                                    "mana_recovery",
                                    new MobEffectInstance(
                                            RECOVERY,
                                            ManaRecoveryResources.rules().strongDuration(),
                                            1)));

    public static final DeferredHolder<MobEffect, MobEffect> RESTORATION =
            EFFECTS.register("mana_restoration", RestorationEffect::new);
    public static final DeferredHolder<Potion, Potion> INSTANT_MANA =
            POTIONS.register(
                    "mana_restoration",
                    () -> new Potion("mana_restoration", new MobEffectInstance(RESTORATION, 1, 0)));
    public static final DeferredHolder<Potion, Potion> STRONG_INSTANT_MANA =
            POTIONS.register(
                    "strong_mana_restoration",
                    () -> new Potion("mana_restoration", new MobEffectInstance(RESTORATION, 1, 1)));

    private static final class RestorationEffect extends InstantenousMobEffect {
        RestorationEffect() {
            super(MobEffectCategory.BENEFICIAL, 0x79def3);
        }

        private void restore(
                net.minecraft.world.entity.LivingEntity target, int amplifier, double scale) {
            if (!Double.isFinite(scale) || scale <= 0) return;
            long base =
                    dev.elementalcircuits.adapter.config.ServerSettings.mana(
                            "ecology.potionRestore");
            long amount =
                    (long)
                            Math.min(
                                    dev.elementalcircuits.domain.Fixed.MAX,
                                    base * (amplifier > 0 ? 2d : 1d) * Math.min(1, scale));
            InstantMana.restore(target, amount);
        }

        @Override
        public boolean applyEffectTick(
                net.minecraft.world.entity.LivingEntity target, int amplifier) {
            restore(target, amplifier, 1);
            return true;
        }

        @Override
        public void applyInstantenousEffect(
                net.minecraft.world.entity.Entity source,
                net.minecraft.world.entity.Entity indirect,
                net.minecraft.world.entity.LivingEntity target,
                int amplifier,
                double scale) {
            restore(target, amplifier, scale);
        }
    }

    private static final class RecoveryEffect extends MobEffect {
        RecoveryEffect() {
            super(MobEffectCategory.BENEFICIAL, 0x4cb9d0);
        }
    }

    private ManaBrewing() {}

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
        POTIONS.register(modBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ManaBrewing::recipes);
    }

    public static void recipes(RegisterBrewingRecipesEvent event) {
        var b = event.getBuilder();
        b.addMix(Potions.AWKWARD, ElementalCircuits.MANA_CRYSTAL.get(), MANA);
        b.addMix(Potions.AWKWARD, ElementalCircuits.MANA_GEL.get(), INSTANT_MANA);
        b.addMix(INSTANT_MANA, Items.GLOWSTONE_DUST, STRONG_INSTANT_MANA);
        b.addMix(MANA, Items.REDSTONE, LONG_MANA);
        b.addMix(MANA, Items.GLOWSTONE_DUST, STRONG_MANA);
    }

    public static void creative(net.minecraft.world.item.CreativeModeTab.Output output) {
        for (var potion : List.of(MANA, LONG_MANA, STRONG_MANA, INSTANT_MANA, STRONG_INSTANT_MANA))
            for (Item bottle : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION))
                output.accept(PotionContents.createItemStack(bottle, potion));
    }
}
