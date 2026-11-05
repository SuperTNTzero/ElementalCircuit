package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.adapter.resources.ElementalCombatResources;
import dev.elementalcircuits.application.observation.SpellSound;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.SecondaryBudget;
import dev.elementalcircuits.domain.combat.DamageAffinity;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.combat.ElementReactionQueue;
import dev.elementalcircuits.domain.combat.ElementalCombat;
import dev.elementalcircuits.domain.mana.Mixture;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Snapshots native conditions, evaluates once, then commits only accepted contacts. */
public final class ElementalCombatAdapter {
    private ElementalCombatAdapter() {}

    public static void apply(
            ServerLevel level,
            UUID owner,
            LivingEntity target,
            Mixture mana,
            Vec3 direction,
            long power) {
        apply(level, owner, target, mana, direction, power, ContactMagic.NEUTRAL);
    }

    static void apply(
            ServerLevel level,
            UUID owner,
            LivingEntity target,
            Mixture mana,
            Vec3 direction,
            long power,
            ContactMagic contact) {
        if (!SpellEffects.eligible(level, owner, target)) return;
        mana = contact.effectScale().apply(mana);
        contact = contact.unscaled();
        var rules = ElementalCombatResources.rules();
        var before =
                new ElementalCombat.State(
                        target.hasEffect(CombatEffects.DRENCHED) || target.isInWaterRainOrBubble(),
                        target.isOnFire(),
                        target.getTicksFrozen(),
                        target.fireImmune() || target.hasEffect(MobEffects.FIRE_RESISTANCE),
                        !target.canFreeze(),
                        target.isInvertedHealAndHarm(),
                        target.isInWaterRainOrBubble(),
                        target.getType().is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES));
        var surviving = ElementReactionQueue.reduce(mana, rules.reactions()).remaining();
        var budget = SecondaryBudget.hit(surviving, contact.effects());
        var plan =
                ElementalCombat.evaluate(
                        contact.effects().hit() ? budget.elemental() : mana,
                        power,
                        before,
                        rules,
                        contact.damageMultiplier());
        // Normalize native frost vulnerability before the common cap, for both pure and mixed hits.
        boolean frostSensitive = target.getType().is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES);
        var source = SpellDamage.source(level, owner, plan, contact.reactive());
        if (target.isInvulnerableTo(source)) return;
        float damage = plan.totalDamage() / (float) Fixed.ONE;
        // A pure IS_FREEZING source receives vanilla's 5x factor inside hurt; compensate exactly
        // once.
        float requested =
                plan.affinity() == DamageAffinity.FROST && frostSensitive ? damage / 5 : damage;
        if (damage > 0 && !target.hurt(source, requested)) return;
        if (damage > 0)
            SpellFeedback.observe(
                    level,
                    owner,
                    false,
                    true,
                    mana.dominant(),
                    (float) Math.clamp(damage / 4, .5, 2));
        if (target.isAlive()) {
            HitModifiers.commit(
                    target, budget, contact.effects(), direction, power, contact.secondary());
            for (var s : plan.steps()) {
                switch (s.wet()) {
                    case APPLY ->
                            target.addEffect(
                                    new MobEffectInstance(
                                            CombatEffects.DRENCHED, rules.status().wetTicks(), 0));
                    case CLEAR -> target.removeEffect(CombatEffects.DRENCHED);
                    default -> {}
                }
                if (s.extinguish()) target.clearFire();
                if (s.igniteSeconds() > 0) target.igniteForSeconds(s.igniteSeconds());
                if (s.fanTicks() > 0) {
                    int current = target.getRemainingFireTicks(),
                            ceiling = rules.status().fireMaxSeconds() * 20;
                    if (current < ceiling)
                        target.setRemainingFireTicks(Math.min(ceiling, current + s.fanTicks()));
                }
                if (target.getTicksFrozen() != s.frozenTicks())
                    target.setTicksFrozen(s.frozenTicks());
                if (s.slowTicks() > 0)
                    target.addEffect(
                            new MobEffectInstance(
                                    MobEffects.MOVEMENT_SLOWDOWN,
                                    s.slowTicks(),
                                    s.slowAmplifier()));
                if (s.levitationTicks() > 0)
                    target.addEffect(
                            new MobEffectInstance(MobEffects.LEVITATION, s.levitationTicks(), 0));
                if (s.purify()) {
                    target.removeEffect(MobEffects.BLINDNESS);
                    target.removeEffect(MobEffects.DARKNESS);
                }
                if (s.blindTicks() > 0)
                    target.addEffect(
                            new MobEffectInstance(MobEffects.BLINDNESS, s.blindTicks(), 0));
            }
            long netImpulse = plan.status().impulse();
            if (netImpulse != 0) {
                double impulse = netImpulse / (double) Fixed.ONE;
                target.push(direction.x * impulse, Math.abs(impulse) * .25, direction.z * impulse);
                target.hurtMarked = true;
            }
            if (plan.healing() > 0) target.heal(plan.healing() / (float) Fixed.ONE);
        }
        Vec3 at = target.getBoundingBox().getCenter();
        Element reaction = CombatReactionFeedback.observe(level, at, plan.reactions());
        if (reaction == null) SpellEffects.contactFeedback(level, at, mana.dominant(), 5);
        else SpellSounds.observe(level, SpellSound.Kind.IMPACT, reaction, at);
    }
}
