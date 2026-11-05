package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.domain.circuit.BoardSafety;
import dev.elementalcircuits.domain.combat.DamageAffinity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Native explosion/event pipeline; one formula-based hurt, ordinary invulnerability frames. */
public final class BoardMeltdown {
    private BoardMeltdown() {}

    public static void explode(
            ServerLevel level, UUID owner, Vec3 origin, BoardSafety.Blast blast) {
        var caster = owner == null ? null : level.getEntity(owner);
        var source = SpellDamage.source(level, caster, DamageAffinity.MIXED);
        var calculator =
                new ExplosionDamageCalculator() {
                    private boolean eligible(Entity entity) {
                        return entity instanceof LivingEntity living
                                && living.isAlive()
                                && !living.isSpectator()
                                && entity.distanceToSqr(origin) < blast.radius() * blast.radius()
                                && (entity.getUUID().equals(owner)
                                        || CastPermissions.mayAffect(level, owner, living));
                    }

                    @Override
                    public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
                        return eligible(entity);
                    }

                    @Override
                    public float getEntityDamageAmount(Explosion explosion, Entity entity) {
                        double attenuation =
                                Math.max(
                                        0,
                                        1 - entity.position().distanceTo(origin) / blast.radius());
                        double exposure = Explosion.getSeenPercent(origin, entity);
                        return (float) (blast.damage() * attenuation * attenuation * exposure);
                    }

                    @Override
                    public float getKnockbackMultiplier(Entity entity) {
                        return eligible(entity) ? 1 : 0;
                    }

                    @Override
                    public boolean shouldBlockExplode(
                            Explosion explosion,
                            BlockGetter world,
                            BlockPos pos,
                            BlockState state,
                            float power) {
                        return pos.getCenter().distanceToSqr(origin)
                                        <= blast.radius() * blast.radius()
                                && CastPermissions.mayChangeBlock(level, owner, pos);
                    }
                };
        // The source entity is deliberately null: the caster must be included among blast victims.
        level.explode(
                null,
                source,
                calculator,
                origin,
                (float) blast.radius(),
                false,
                ServerSettings.count("board.breakBlocks") == 1
                        ? Level.ExplosionInteraction.BLOCK
                        : Level.ExplosionInteraction.NONE);
    }
}
