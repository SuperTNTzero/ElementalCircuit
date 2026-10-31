package dev.elementalcircuits.server;

import dev.elementalcircuits.domain.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Spends a separate share for each modifier; elemental impact receives only its remainder. */
final class HitModifiers {
    private HitModifiers(){ }
    static Mixture apply(ServerLevel level,UUID owner,Vec3 center,Vec3 incoming,int radius,Packet packet,long strength,SecondaryRules rules,Set<UUID> excluded,LivingEntity direct,boolean area){
        var budget=SecondaryBudget.hit(packet.mana(),packet.spell().effects());if(!packet.spell().effects().hit())return budget.elemental();
        var targets=area?SpellEffects.targets(level,owner,center,radius,packet.mana(),5,excluded):direct!=null && SpellEffects.eligible(level,owner,direct) && !excluded.contains(direct.getUUID())?List.of(direct):List.<LivingEntity>of();
        if(targets.isEmpty())return budget.elemental();int[] weights=new int[targets.size()];Arrays.fill(weights,1);
        var e=packet.spell().effects();
        for(int type=0;type<3;type++){
            var shares=budget.modifiers().get(type).split(weights);
            for(int i=0;i<targets.size();i++){
                var target=targets.get(i);long amount=shares.get(i).total();if(amount==0)continue;
                if(type==0){int ticks=SecondaryBudget.duration(amount,strength,e.ignite(),rules);if(ticks>0)target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(),ticks));}
                else if(type==1){int ticks=SecondaryBudget.duration(amount,strength,e.slow(),rules);if(ticks>0)target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,ticks,0));}
                else {double force=SecondaryBudget.impulse(amount,strength,e.knockback(),rules)/(double)Fixed.ONE;if(force>0){Vec3 d=target.getBoundingBox().getCenter().subtract(center);if(d.horizontalDistanceSqr()<1e-8)d=incoming;d=new Vec3(d.x,0,d.z).normalize();target.push(d.x*force,force*.25,d.z*force);target.hurtMarked=true;}}
            }
        }
        return budget.elemental();
    }
}
