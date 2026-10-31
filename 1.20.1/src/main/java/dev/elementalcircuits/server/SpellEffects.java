package dev.elementalcircuits.server;

import dev.elementalcircuits.domain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Sole authoritative Minecraft effect adapter. Particles never decide hits or lifetime. */
public final class SpellEffects {
    private SpellEffects(){ }
    public static void cast(ServerLevel level,Entity owner,Vec3 origin,Vec3 look,UUID carrier,MagicCircuit.Cast cast,Rules rules){
        cast(level,owner,origin,look,carrier,cast,rules,Fixed.ONE);
    }
    public static void cast(ServerLevel level,Entity owner,Vec3 origin,Vec3 look,UUID carrier,MagicCircuit.Cast cast,Rules rules,long strength){
        castOwned(level,owner==null?null:owner.getUUID(),origin,look,carrier,cast,rules,strength);
    }
    public static void castOwned(ServerLevel level,UUID ownerId,Vec3 origin,Vec3 look,UUID carrier,MagicCircuit.Cast cast,Rules rules,long strength){
        if(strength<Fixed.ONE/4 || strength>2*Fixed.ONE)throw new IllegalArgumentException("非法法术强度");
        SpellSpec spec=cast.spell();
        if(!spec.effects().empty() && !spec.compatibleOutput())return;
        if(Arrays.stream(cast.mana().values()).skip(1).noneMatch(amount->amount>0))return;
        int range=spec.range()>0?spec.range():(int)rules.range(), radius=spec.effects().impactRadius(spec.radius()>0?spec.radius():(int)rules.radius());
        Vec3 direction=scatter(look,carrier,cast.sequence(),spec.aim()<0?8192:spec.aim());
        if(spec.shape()==SpellSpec.Shape.SHIELD){
            Vec3 end=trace(level,ownerId,origin,origin.add(direction.scale(1.5))).position();
            WorldSpells.get(level).shield(ownerId,end,direction,radius,cast.mana().total()*strength/Fixed.ONE);
            SpellSounds.observe(level,dev.elementalcircuits.application.observation.SpellSound.Kind.SHIELD,cast.mana().dominant(),end);return;
        }
        SpellSounds.observe(level,dev.elementalcircuits.application.observation.SpellSound.Kind.CAST,cast.mana().dominant(),origin);
        if(spec.method()!=SpellSpec.Method.DIRECTED){for(Packet packet:SpellPayloads.split(new Packet(cast.mana(),spec)))impact(level,ownerId,origin,radius,packet.mana(),strength);return;}
        if(spec.shape()==SpellSpec.Shape.BEAM){
            for(Packet packet:SpellPayloads.split(new Packet(cast.mana(),spec))){
                Trace hit=trace(level,ownerId,origin,origin.add(direction.scale(range)),true,ElementInteractions.hasWind(packet.mana()),SpellPayloads.phased(packet.mana()),packet.mana().values()[Element.SHADOW.component(false)]>0);
                line(level,origin,hit.position(),packet.mana().dominant());
                if(hit.blocked())SpellSounds.observe(level,dev.elementalcircuits.application.observation.SpellSound.Kind.IMPACT,packet.mana().dominant(),hit.position());
                Packet remaining=PathIllumination.beam(level,ownerId,origin,hit.position(),packet,strength);
                if(spec.effects().secondary() || spec.effects().hit())WorldSpells.get(level).beamContact(level,ownerId,hit,direction,Math.max(0,range-origin.distanceTo(hit.position())),radius,remaining,strength);
                else if(spec.effects().burst()>0){Vec3 at=hit.position().subtract(direction.scale(hit.block()!=null?.03:0));impact(level,ownerId,at,radius,remaining.mana(),strength);}
                else ElementInteractions.beam(level,ownerId,origin,hit,remaining.mana(),strength);
            }
        }else WorldSpells.get(level).launch(ownerId,origin,direction,range,radius,new Packet(cast.mana(),spec),strength);
    }
    private static Vec3 scatter(Vec3 look,UUID id,long sequence,int aim){
        long x=id.getMostSignificantBits()^id.getLeastSignificantBits()^(sequence*0x9e3779b97f4a7c15L);
        x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;x=(x^(x>>>27))*0x94d049bb133111ebL;x^=x>>>31;
        double a=((x&65535)/32767.5-1)*aim/Fixed.ONE,b=(((x>>>16)&65535)/32767.5-1)*aim/Fixed.ONE;
        Vec3 n=look.normalize(),right=n.cross(Math.abs(n.y)>0.99?new Vec3(1,0,0):new Vec3(0,1,0)).normalize(),up=right.cross(n).normalize();
        return n.add(right.scale(a)).add(up.scale(b)).normalize();
    }
    public record Trace(Vec3 position,Entity entity,BlockHitResult block,boolean blocked){ }
    static Trace trace(ServerLevel level,UUID owner,Vec3 from,Vec3 to){
        return trace(level,owner,from,to,false,false);
    }
    static Trace trace(ServerLevel level,UUID owner,Vec3 from,Vec3 to,boolean world,boolean items){
        return trace(level,owner,from,to,world,items,false);
    }
    static Trace trace(ServerLevel level,UUID owner,Vec3 from,Vec3 to,boolean world,boolean items,boolean phased){
        return trace(level,owner,from,to,world,items,phased,false);
    }
    static Trace trace(ServerLevel level,UUID owner,Vec3 from,Vec3 to,boolean world,boolean items,boolean phased,boolean sources){
        return trace(level,owner,from,to,world,items,phased,sources,Set.of());
    }
    static Trace trace(ServerLevel level,UUID owner,Vec3 from,Vec3 to,boolean world,boolean items,boolean phased,boolean sources,Set<UUID> excluded){
        if(world && !loadedBetween(level,from,to))return new Trace(from,null,null,false);
        BlockHitResult block=phased?BlockHitResult.miss(to,net.minecraft.core.Direction.UP,BlockPos.containing(to)):level.clip(new ClipContext(from,to,sources?ClipContext.Block.OUTLINE:ClipContext.Block.COLLIDER,world?ClipContext.Fluid.ANY:ClipContext.Fluid.NONE,(Entity)null));
        Vec3 end=block.getType()==HitResult.Type.MISS?to:block.getLocation();
        double distance=from.distanceToSqr(end);Entity target=null;
        List<Entity> candidates=level.getEntitiesOfClass(Entity.class,new AABB(from,end).inflate(1),e->e instanceof LivingEntity living && eligible(level,owner,living) && !excluded.contains(e.getUUID())
            || items && e instanceof ItemEntity && e.isAlive());
        candidates.sort(Comparator.comparing(Entity::getUUID));
        for(Entity e:candidates){Optional<Vec3> hit=e.getBoundingBox().inflate(0.1).clip(from,end);
            if(hit.isPresent() && from.distanceToSqr(hit.get())<distance){distance=from.distanceToSqr(hit.get());end=hit.get();target=e;}}
        return new Trace(end,target,target==null && block.getType()!=HitResult.Type.MISS?block:null,target!=null || block.getType()!=HitResult.Type.MISS);
    }
    static boolean loadedBetween(ServerLevel level,Vec3 from,Vec3 to){
        // Only X/Z determine chunk availability. A ray may end below the world after hitting its floor.
        return level.hasChunksAt(BlockPos.containing(Math.min(from.x,to.x),level.getMinBuildHeight(),Math.min(from.z,to.z)),
            BlockPos.containing(Math.max(from.x,to.x),level.getMinBuildHeight(),Math.max(from.z,to.z)));
    }
    static boolean eligible(ServerLevel level,UUID owner,LivingEntity target){
        return CastPermissions.mayAffect(level,owner,target);
    }
    static void impact(ServerLevel level,UUID owner,Vec3 center,int radius,Mixture mana){
        impact(level,owner,center,radius,mana,Fixed.ONE);
    }
    static void impact(ServerLevel level,UUID owner,Vec3 center,int radius,Mixture mana,long strength){
        impact(level,owner,center,radius,mana,strength,3);
    }
    static void impact(ServerLevel level,UUID owner,Vec3 center,int radius,Mixture mana,long strength,int effects){
        impact(level,owner,center,radius,mana,strength,effects,Set.of());
    }
    static List<LivingEntity> targets(ServerLevel level,UUID owner,Vec3 center,int radius,Mixture mana,int effects,Set<UUID> excluded){
        boolean world=effects>=2,phased=effects>=3 && SpellPayloads.phased(mana);
        List<LivingEntity> targets=level.getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),e->eligible(level,owner,e) && !excluded.contains(e.getUUID())
            && e.getBoundingBox().getCenter().distanceToSqr(center)<=radius*radius
            && (!world || loadedBetween(level,center,e.getBoundingBox().getCenter()))
            && (phased || level.clip(new ClipContext(center,e.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,(Entity)null)).getType()==HitResult.Type.MISS));
        targets.sort(Comparator.comparing(Entity::getUUID));
        return targets;
    }
    static void impact(ServerLevel level,UUID owner,Vec3 center,int radius,Mixture mana,long strength,int effects,Set<UUID> excluded){
        if(mana.empty())return;boolean world=effects>=2;var targets=targets(level,owner,center,radius,mana,effects,excluded);
        if(world)ElementInteractions.area(level,owner,center,radius,mana,strength,targets,effects>=3);
        else if(!targets.isEmpty()){
            int[] weights=new int[targets.size()];Arrays.fill(weights,1);List<Mixture> shares=mana.split(weights);
            for(int i=0;i<targets.size();i++)apply(level,owner,targets.get(i),shares.get(i),targets.get(i).position().subtract(center).normalize(),strength);
        }
        contactFeedback(level,center,mana.dominant(),16);
    }
    static void apply(ServerLevel level,UUID owner,LivingEntity target,Mixture mana,Vec3 direction,long power){
        Entity caster=owner==null?null:level.getEntity(owner);long[] values=mana.values();float damage=0,healing=0;
        for(Element element:Element.values())if(element!=Element.PURE)for(int phase=0;phase<2;phase++){
            double strength=values[element.component(phase==1)]/(double)Fixed.ONE*power/Fixed.ONE;if(strength<=0)continue;boolean inverse=phase==1;
            switch(element){
                case LIGHT -> {if(inverse)damage+=strength*2;else healing+=strength*2;}
                case FIRE -> {damage+=strength*2;if(strength>=0.1){if(inverse){target.clearFire();target.setTicksFrozen(Math.min(300,target.getTicksFrozen()+(int)(strength*40)));}else target.setSecondsOnFire(Math.min(4,Math.max(1,(int)strength)));}}
                case WATER -> {target.clearFire();if(strength>=0.1)target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,Math.min(100,(int)(strength*30)),0));}
                case WIND -> {double impulse=Math.min(1.0,strength*0.25)*(inverse?-1:1);target.push(direction.x*impulse,Math.abs(impulse)*0.25,direction.z*impulse);target.hurtMarked=true;}
                case EARTH -> {damage+=strength;if(strength>=0.1)target.addEffect(new MobEffectInstance(inverse?MobEffects.LEVITATION:MobEffects.MOVEMENT_SLOWDOWN,Math.min(40,(int)(strength*15)),0));}
                case SHADOW -> {damage+=strength;if(strength>=0.1){if(inverse)target.removeEffect(MobEffects.BLINDNESS);else target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,Math.min(80,(int)(strength*25)),0));}}
                case LIGHTNING, ARCANE -> damage+=strength*2;
                default -> { }
            }
        }
        if(damage>0)target.hurt(level.damageSources().indirectMagic(caster,caster),Math.min(128,damage));
        if(healing>0)target.heal(Math.min(128,healing));
        contactFeedback(level,target.getBoundingBox().getCenter(),mana.dominant(),5);
    }
    static void line(ServerLevel level,Vec3 from,Vec3 to,Element element){SpellVisuals.observe(level,dev.elementalcircuits.application.observation.SpellVisual.Kind.BEAM,element,from,to,1,1);}
    static void contactFeedback(ServerLevel level,Vec3 p,Element element,int count){
        SpellVisuals.observe(level,dev.elementalcircuits.application.observation.SpellVisual.Kind.IMPACT,element,p,p,1,Math.max(1,Math.min(32,count)));
        SpellSounds.observe(level,dev.elementalcircuits.application.observation.SpellSound.Kind.IMPACT,element,p);
    }
}
