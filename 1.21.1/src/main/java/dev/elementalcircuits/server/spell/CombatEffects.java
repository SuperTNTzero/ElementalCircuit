package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.ElementalCircuits;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

/** Vanilla owns duration, synchronization, saves and milk curing. */
public final class CombatEffects {
    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, ElementalCircuits.ID);
    public static final DeferredHolder<MobEffect, MobEffect> DRENCHED =
            EFFECTS.register("drenched", Drenched::new);

    private static final class Drenched extends MobEffect {
        Drenched() {
            super(MobEffectCategory.HARMFUL, 0x5baee7);
        }
    }

    private CombatEffects() {}

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
    }
}
