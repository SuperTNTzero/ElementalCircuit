package dev.elementalcircuits.server;

import dev.elementalcircuits.ElementalCircuits;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.Entity;

/** Native magic damage without 1.21's automatic, unpaid hurt knockback. */
final class SpellDamage {
    private static final ResourceKey<DamageType> TYPE=ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath(ElementalCircuits.ID,"spell"));
    private SpellDamage(){}
    static DamageSource source(ServerLevel level,Entity caster){return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TYPE),caster,caster);}
}
