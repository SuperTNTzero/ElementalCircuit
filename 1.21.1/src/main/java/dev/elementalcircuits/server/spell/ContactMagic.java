package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.adapter.resources.SecondaryResources;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.ProjectileEffects;
import dev.elementalcircuits.domain.casting.SecondaryRules;
import dev.elementalcircuits.domain.casting.SpellSpec;

/** Frozen invocation data accompanying a contact through every geometry adapter. */
record ContactMagic(
        long damageMultiplier,
        ProjectileEffects effects,
        SecondaryRules secondary,
        boolean reactive,
        dev.elementalcircuits.domain.casting.EffectScale effectScale,
        dev.elementalcircuits.domain.casting.MaterialPorts materials) {
    ContactMagic(
            long damageMultiplier,
            ProjectileEffects effects,
            SecondaryRules secondary,
            boolean reactive,
            dev.elementalcircuits.domain.casting.EffectScale effectScale) {
        this(
                damageMultiplier,
                effects,
                secondary,
                reactive,
                effectScale,
                dev.elementalcircuits.domain.casting.MaterialPorts.NONE);
    }

    ContactMagic(
            long damageMultiplier,
            ProjectileEffects effects,
            SecondaryRules secondary,
            boolean reactive) {
        this(
                damageMultiplier,
                effects,
                secondary,
                reactive,
                dev.elementalcircuits.domain.casting.EffectScale.IDENTITY);
    }

    ContactMagic unscaled() {
        return new ContactMagic(
                damageMultiplier,
                effects,
                secondary,
                reactive,
                dev.elementalcircuits.domain.casting.EffectScale.IDENTITY,
                materials);
    }

    ContactMagic(long damageMultiplier, ProjectileEffects effects, SecondaryRules secondary) {
        this(damageMultiplier, effects, secondary, false);
    }

    static final ContactMagic NEUTRAL =
            new ContactMagic(Fixed.ONE, ProjectileEffects.NONE, SecondaryResources.rules());

    static ContactMagic of(SpellSpec spec) {
        return new ContactMagic(
                spec.damageMultiplier(),
                spec.effects(),
                SecondaryResources.rules(),
                spec.reactive(),
                spec.effectScale(),
                spec.materials());
    }
}
