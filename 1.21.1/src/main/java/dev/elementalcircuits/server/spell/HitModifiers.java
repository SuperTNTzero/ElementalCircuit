package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.ProjectileEffects;
import dev.elementalcircuits.domain.casting.SecondaryBudget;
import dev.elementalcircuits.domain.casting.SecondaryRules;

import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** One paid contact plan: the caller commits this only after native hurt accepts the same hit. */
final class HitModifiers {
    private HitModifiers() {}

    static void commit(
            LivingEntity target,
            SecondaryBudget.Hit budget,
            ProjectileEffects effects,
            Vec3 incoming,
            long strength,
            SecondaryRules rules) {
        int ignite =
                SecondaryBudget.duration(
                        budget.modifiers().get(0).total(), strength, effects.ignite(), rules);
        int slow =
                SecondaryBudget.duration(
                        budget.modifiers().get(1).total(), strength, effects.slow(), rules);
        long push =
                SecondaryBudget.impulse(
                        budget.modifiers().get(2).total(), strength, effects.knockback(), rules);
        if (ignite > 0 && !target.fireImmune() && !target.hasEffect(MobEffects.FIRE_RESISTANCE))
            target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), ignite));
        if (slow > 0)
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slow, 0));
        if (push > 0) {
            Vec3 d = new Vec3(incoming.x, 0, incoming.z).normalize();
            double force = push / (double) Fixed.ONE;
            target.push(d.x * force, force * .25, d.z * force);
            target.hurtMarked = true;
        }
    }
}
