package dev.elementalcircuits.server;

import dev.elementalcircuits.domain.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Plans bounded children and spends one contact budget. WorldSpells schedules children next step. */
final class SecondarySpells {
    record Birth(Vec3 position,Vec3 direction,double range,Packet packet,DerivationState state){ }
    record Contact(Packet primary,List<Birth> children){public Contact{children=List.copyOf(children);}}
    private SecondarySpells(){ }
    private static SpellSpec childSpec(SpellSpec s){return new SpellSpec(SpellSpec.Method.DIRECTED,SpellSpec.Shape.SPHERE,s.amount(),s.range(),s.radius(),s.aim(),s.effects());}
    static Contact contact(ServerLevel level,UUID owner,Vec3 at,Vec3 direction,double remaining,SpellEffects.Trace trace,Packet packet,DerivationState state,SecondaryRules rules){
        boolean split=trace.blocked() && state.splitReady(),chain=trace.blocked() && state.chainLeft()>0;
        var budget=SecondaryBudget.contact(packet.mana(),split,chain);var births=new ArrayList<Birth>();
        var visited=state.afterHit(trace.entity() instanceof LivingEntity?trace.entity().getUUID():null);
        double range=Math.min(remaining,rules.childRange());
        if(range>.05 && split){
            var mana=budget.fragments().retain(rules.retention());int count=packet.spell().effects().fragments();int[] weights=new int[count];Arrays.fill(weights,1);var shares=mana.split(weights);
            Vec3 axis=trace.block()==null?direction:new Vec3(trace.block().getDirection().getStepX(),trace.block().getDirection().getStepY(),trace.block().getDirection().getStepZ());
            Vec3 right=axis.cross(Math.abs(axis.y)>.99?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
            for(int i=0;i<count;i++)if(!shares.get(i).empty()){
                double angle=Math.toRadians(rules.fanDegrees())*(2.0*i/(count-1)-1);Vec3 d=axis.scale(Math.cos(angle)).add(right.scale(Math.sin(angle))).normalize();
                births.add(new Birth(at.add(axis.scale(.03)),d,range-.03,new Packet(shares.get(i),childSpec(packet.spell())),new DerivationState(false,0,visited)));
            }
        }
        if(range>.05 && chain){
            var targets=level.getEntitiesOfClass(LivingEntity.class,new AABB(at,at).inflate(rules.chainRange()),e->SpellEffects.eligible(level,owner,e) && !visited.contains(e.getUUID()) && e.getBoundingBox().getCenter().distanceToSqr(at)<=rules.chainRange()*rules.chainRange() && SpellEffects.loadedBetween(level,at,e.getBoundingBox().getCenter()) && level.clip(new ClipContext(at,e.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,(Entity)null)).getType()==HitResult.Type.MISS);
            targets.sort(Comparator.comparingDouble((LivingEntity e)->e.getBoundingBox().getCenter().distanceToSqr(at)).thenComparing(Entity::getUUID));
            var mana=budget.chain().retain(rules.retention());
            if(!targets.isEmpty() && !mana.empty()){
                Vec3 d=targets.get(0).getBoundingBox().getCenter().subtract(at).normalize();
                if(d.lengthSqr()>.5)births.add(new Birth(at.add(d.scale(.03)),d,range-.03,new Packet(mana,childSpec(packet.spell())),new DerivationState(false,state.chainLeft()-1,visited)));
            }
        }
        return new Contact(budget.impact().empty()?null:new Packet(budget.impact(),packet.spell()),births);
    }
}
