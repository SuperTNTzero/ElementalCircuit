package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.domain.combat.DamageAffinity;

import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.Entity;

import java.util.*;

/** One native hit with immutable pre-protection 16.16 damage components for integrations. */
public final class ElementalDamageSource extends DamageSource {
    private final Map<DamageAffinity, Long> components;
    private final UUID ownerId;
    private final boolean reactive;

    public boolean reactive() {
        return reactive;
    }

    ElementalDamageSource(
            Holder<DamageType> type,
            Entity caster,
            UUID ownerId,
            Map<DamageAffinity, Long> components) {
        this(type, caster, ownerId, components, false);
    }

    ElementalDamageSource(
            Holder<DamageType> type,
            Entity caster,
            UUID ownerId,
            Map<DamageAffinity, Long> components,
            boolean reactive) {
        super(type, caster, caster);
        this.reactive = reactive;
        this.components = Map.copyOf(components);
        this.ownerId = ownerId;
    }

    public Map<DamageAffinity, Long> components() {
        return components;
    }

    public UUID ownerId() {
        return ownerId;
    }
}
