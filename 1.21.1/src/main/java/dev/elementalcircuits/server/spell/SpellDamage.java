package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.combat.DamageAffinity;
import dev.elementalcircuits.domain.combat.ElementalCombat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.Entity;

import java.util.*;

/** Data-driven native registry keys; no deferred registration for datapack damage types. */
public final class SpellDamage {
    private static final Map<DamageAffinity, ResourceKey<DamageType>> KEYS =
            new EnumMap<>(DamageAffinity.class);
    public static final ResourceKey<DamageType> GENERIC = key("ec_damage");

    static {
        for (var affinity : DamageAffinity.values()) KEYS.put(affinity, key(affinity.path()));
    }

    private SpellDamage() {}

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(
                Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath(ElementalCircuits.ID, path));
    }

    public static ResourceKey<DamageType> type(DamageAffinity affinity) {
        return Objects.requireNonNull(KEYS.get(affinity));
    }

    public static DamageSource source(ServerLevel level, Entity caster, DamageAffinity affinity) {
        return new DamageSource(
                level.registryAccess()
                        .registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(type(affinity)),
                caster,
                caster);
    }

    static ElementalDamageSource source(ServerLevel level, UUID owner, ElementalCombat.Plan plan) {
        return source(level, owner, plan, false);
    }

    static ElementalDamageSource source(
            ServerLevel level, UUID owner, ElementalCombat.Plan plan, boolean reactive) {
        return new ElementalDamageSource(
                level.registryAccess()
                        .registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(type(plan.affinity())),
                owner == null ? null : level.getEntity(owner),
                owner,
                plan.damage(),
                reactive);
    }
}
