package dev.elementalcircuits.server;

import dev.elementalcircuits.adapter.WorldInteractionResources;
import dev.elementalcircuits.adapter.RadianceResources;
import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.block.DimmedLightBlock;
import dev.elementalcircuits.domain.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.MinecraftForge;
import java.util.*;

/** World contacts, finite allocation, then authoritative responses. No inventory creation or client writes. */
public final class ElementInteractions {
    private static final WorldInteractionRules RULES=WorldInteractionResources.rules();
    private record Contact(String key,Entity entity,BlockPos pos,BlockState before,Direction face,List<Long> minimums,boolean radiance) { }
    private record Change(BlockState after,long cost,boolean rod,boolean steam,int light) { }
    private ElementInteractions(){ }

    static void beam(ServerLevel level,UUID owner,Vec3 origin,SpellEffects.Trace trace,Mixture mana,long strength){
        List<Contact> contacts=new ArrayList<>();
        if(trace.entity()!=null)contacts.add(entity(trace.entity()));
        else if(trace.block()!=null){
            addBlock(level,owner,trace.block().getBlockPos(),trace.block().getDirection(),mana,strength,contacts,true);
            addBlock(level,owner,trace.block().getBlockPos().relative(trace.block().getDirection()),trace.block().getDirection(),mana,strength,contacts,true);
        }
        else if(mana.values()[Element.LIGHT.component(false)]>0)addBlock(level,owner,BlockPos.containing(trace.position()),Direction.UP,mana,strength,contacts,true);
        resolve(level,owner,origin,mana,strength,contacts);
    }
    static void area(ServerLevel level,UUID owner,Vec3 center,int radius,Mixture mana,long strength,List<LivingEntity> living,boolean radiance){
        List<Contact> contacts=new ArrayList<>();living.forEach(e->contacts.add(entity(e)));
        if(hasWind(mana)){
            List<ItemEntity> items=level.getEntitiesOfClass(ItemEntity.class,new AABB(center,center).inflate(radius),e->e.isAlive()
                && e.position().distanceToSqr(center)<=radius*radius && visible(level,center,e.getBoundingBox().getCenter(),null));
            items.forEach(e->contacts.add(entity(e)));
        }
        BlockPos base=BlockPos.containing(center);
        long[] components=mana.values();boolean blocks=false;
        for(Element element:new Element[]{Element.FIRE,Element.WATER,Element.LIGHTNING})blocks|=components[element.component(false)]>0 || components[element.component(true)]>0;
        if(radiance)blocks|=components[Element.LIGHT.component(false)]>0 || components[Element.SHADOW.component(false)]>0;
        if(blocks)
        for(int x=-radius;x<=radius;x++)for(int y=-radius;y<=radius;y++)for(int z=-radius;z<=radius;z++){
            BlockPos pos=base.offset(x,y,z);
            if(Vec3.atCenterOf(pos).distanceToSqr(center)>radius*radius || !level.hasChunkAt(pos))continue;
            BlockState state=level.getBlockState(pos);
            // Area creation uses a surface, avoiding an entire floating volume of sources.
            if(state.isAir() && !(radiance && components[Element.LIGHT.component(false)]>0) && (!level.hasChunkAt(pos.below()) || !level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP)))continue;
            int before=contacts.size();addBlock(level,owner,pos,Direction.UP,mana,strength,contacts,radiance);
            if(contacts.size()>before && !visible(level,center,Vec3.atCenterOf(pos),pos))contacts.remove(contacts.size()-1);
        }
        resolve(level,owner,center,mana,strength,contacts);
    }
    static boolean hasWind(Mixture mana){long[] v=mana.values();return v[Element.WIND.component(false)]>0 || v[Element.WIND.component(true)]>0;}
    private static boolean visible(ServerLevel level,Vec3 from,Vec3 to,BlockPos target){
        if(!SpellEffects.loadedBetween(level,from,to))return false;
        BlockHitResult hit=level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,(Entity)null));
        return hit.getType()==HitResult.Type.MISS || target!=null && hit.getBlockPos().equals(target);
    }
    private static Contact entity(Entity entity){
        List<Long> minimums=new ArrayList<>(Collections.nCopies(Mixture.COMPONENTS,0L));
        for(Element element:Element.values())if(element!=Element.PURE && (entity instanceof LivingEntity || element==Element.WIND)){
            minimums.set(element.component(false),1L);minimums.set(element.component(true),1L);
        }
        return new Contact("E:"+entity.getUUID(),entity,null,null,null,minimums,false);
    }
    static boolean permitted(ServerLevel level,UUID owner,BlockPos pos){
        return CastPermissions.mayChangeBlock(level,owner,pos);
    }
    private static void addBlock(ServerLevel level,UUID owner,BlockPos pos,Direction face,Mixture mana,long strength,List<Contact> contacts,boolean radiance){
        if(!permitted(level,owner,pos) || !SpellEffects.loadedBetween(level,Vec3.atCenterOf(pos).add(-1,0,-1),Vec3.atCenterOf(pos).add(1,0,1)))return;
        BlockState before=level.getBlockState(pos);long[] amounts=mana.values();
        List<Long> minimums=new ArrayList<>(Collections.nCopies(Mixture.COMPONENTS,0L));boolean responds=false;
        for(Element element:Element.values())if(element!=Element.PURE)for(int phase=0;phase<2;phase++){
            int c=element.component(phase==1);if(amounts[c]==0)continue;
            Change change=change(level,pos,before,element,phase==1,radiance);
            if(change!=null){minimums.set(c,RULES.required(change.cost,strength));responds=true;}
        }
        if(responds)contacts.add(new Contact("B:"+pos.getX()+":"+pos.getY()+":"+pos.getZ(),null,pos.immutable(),before,face,minimums,radiance));
    }
    private static boolean lampFire(BlockState s){return s.is(BlockTags.CAMPFIRES) || s.getBlock() instanceof CandleBlock || s.getBlock() instanceof CandleCakeBlock;}
    private static Change change(ServerLevel level,BlockPos pos,BlockState s,Element element,boolean inverse,boolean radiance){
        BlockState next=null;boolean rod=false,steam=false;long cost=RULES.blockCost();
        if(radiance && !inverse && element==Element.LIGHT && (s.isAir() || s.is(ElementalCircuits.SPELL_LIGHT.get())) && WorldIllumination.get(level).canTrack(pos))
            return new Change(ElementalCircuits.SPELL_LIGHT.get().defaultBlockState(),RULES.sourceCost(),false,false,1);
        if(radiance && !inverse && element==Element.SHADOW){
            if(s.is(ElementalCircuits.SPELL_LIGHT.get()))return new Change(Blocks.AIR.defaultBlockState(),cost,false,false,3);
            if(s.getBlock() instanceof BaseFireBlock)return new Change(Blocks.AIR.defaultBlockState(),cost,false,false,0);
            if(lampFire(s) && s.getValue(BlockStateProperties.LIT))return new Change(s.setValue(BlockStateProperties.LIT,false),cost,false,false,0);
            BlockState dimmed=s.is(ElementalCircuits.DIMMED_LIGHT.get())?s:DimmedLightBlock.mask(s);
            if(dimmed!=null && WorldIllumination.get(level).canTrack(pos))return new Change(dimmed,cost,false,false,2);
        }
        if(element==Element.FIRE){
            if(!inverse){
                if(lampFire(s) && !s.getValue(BlockStateProperties.LIT) && (!s.hasProperty(BlockStateProperties.WATERLOGGED) || !s.getValue(BlockStateProperties.WATERLOGGED)))next=s.setValue(BlockStateProperties.LIT,true);
                else if(s.is(Blocks.ICE) || s.is(Blocks.FROSTED_ICE))next=level.dimensionType().ultraWarm()?Blocks.AIR.defaultBlockState():Blocks.WATER.defaultBlockState();
                else if(s.isAir()){
                    BlockState fire=BaseFireBlock.getState(level,pos);if(fire.canSurvive(level,pos)){next=fire;cost=RULES.sourceCost();}
                }
            }else{
                if(s.getBlock() instanceof BaseFireBlock)next=Blocks.AIR.defaultBlockState();
                else if(lampFire(s) && s.getValue(BlockStateProperties.LIT))next=s.setValue(BlockStateProperties.LIT,false);
                else if(s.is(Blocks.WATER) && s.getFluidState().isSource())next=Blocks.FROSTED_ICE.defaultBlockState();
            }
        }else if(element==Element.WATER){
            if(!inverse){
                if(s.getBlock() instanceof BaseFireBlock)next=Blocks.AIR.defaultBlockState();
                else if(lampFire(s) && s.getValue(BlockStateProperties.LIT))next=s.setValue(BlockStateProperties.LIT,false);
                else if(s.is(Blocks.FARMLAND) && s.getValue(FarmBlock.MOISTURE)<7)next=s.setValue(FarmBlock.MOISTURE,7);
                else if(s.isAir()){cost=RULES.sourceCost();steam=level.dimensionType().ultraWarm();next=steam?Blocks.AIR.defaultBlockState():Blocks.WATER.defaultBlockState();}
            }else if(s.is(Blocks.FARMLAND) && s.getValue(FarmBlock.MOISTURE)>0)next=s.setValue(FarmBlock.MOISTURE,0);
        }else if(element==Element.LIGHTNING){
            if(s.is(Blocks.LIGHTNING_ROD)){
                if(s.getValue(LightningRodBlock.POWERED)==inverse){next=s.setValue(LightningRodBlock.POWERED,!inverse);rod=!inverse;}
            }else if(!inverse)next=WeatheringCopper.getPrevious(s).orElse(null);
        }
        return next==null?null:new Change(next,cost,rod,steam,0);
    }
    private static void resolve(ServerLevel level,UUID owner,Vec3 origin,Mixture mana,long strength,List<Contact> candidates){
        // Duplicate physical contacts (e.g. impact and adjacent cell) remain one recipient.
        Map<String,Contact> unique=new TreeMap<>();for(Contact c:candidates)unique.putIfAbsent(c.key,c);
        List<Contact> contacts=unique.values().stream().sorted(Comparator.comparingDouble((Contact c)->c.entity!=null?c.entity.position().distanceToSqr(origin):Vec3.atCenterOf(c.pos).distanceToSqr(origin)).thenComparing(Contact::key)).limit(RULES.maxTargets()).toList();
        Map<String,Contact> byKey=new HashMap<>();List<EffectDistribution.Target> targets=new ArrayList<>();
        for(Contact c:contacts){byKey.put(c.key,c);targets.add(new EffectDistribution.Target(c.key,c.minimums));}
        EffectDistribution.Plan plan=EffectDistribution.allocate(mana,targets);
        for(var share:plan.shares()){
            Contact c=byKey.get(share.key());
            if(c.entity instanceof LivingEntity living){if(living.isAlive())SpellEffects.apply(level,owner,living,share.mana(),living.position().subtract(origin).normalize(),strength);}
            else if(c.entity instanceof ItemEntity item){
                long[] v=share.mana().values();double net=(v[Element.WIND.component(false)]-v[Element.WIND.component(true)])/(double)Fixed.ONE*strength/Fixed.ONE;
                double impulse=Math.max(-RULES.maxImpulse()/(double)Fixed.ONE,Math.min(RULES.maxImpulse()/(double)Fixed.ONE,net*RULES.impulsePerUnit()/Fixed.ONE));
                Vec3 direction=item.position().subtract(origin).normalize();item.push(direction.x*impulse,direction.y*impulse,direction.z*impulse);item.hurtMarked=true;
            }else if(c.pos!=null)applyBlock(level,owner,c,share.mana(),strength);
        }
        // Unresponsive, unaffordable or invalidated shares dissipate; never become mana refunds.
    }
    private static void applyBlock(ServerLevel level,UUID owner,Contact c,Mixture mana,long strength){
        if(!permitted(level,owner,c.pos) || !level.getBlockState(c.pos).equals(c.before))return;long[] values=mana.values();
        for(Element element:Element.values())if(element!=Element.PURE)for(int phase=0;phase<2;phase++){
            Change change=change(level,c.pos,c.before,element,phase==1,c.radiance);
            if(change==null || values[element.component(phase==1)]<RULES.required(change.cost,strength))continue;
            Entity caster=owner==null?null:level.getEntity(owner);
            BlockSnapshot snapshot=BlockSnapshot.create(level.dimension(),level,c.pos);
            // Check cancellation before placement: native water/lava reactions can happen inside setBlock.
            if(MinecraftForge.EVENT_BUS.post(new ElementBlockChangeEvent(snapshot,level.getBlockState(c.pos.relative(c.face.getOpposite())),caster,change.after)))return;
            if(!permitted(level,owner,c.pos) || !level.getBlockState(c.pos).equals(c.before))return;
            if(!change.steam && !change.after.equals(c.before) && !level.setBlock(c.pos,change.after,3))return;
            if(change.light==1)WorldIllumination.get(level).adopt(c.pos,change.after,RadianceResources.rules().lightLife());
            else if(change.light==2)WorldIllumination.get(level).adopt(c.pos,change.after,RadianceResources.rules().shadowLife());
            else if(change.light==3)WorldIllumination.get(level).forget(c.pos);
            if(change.steam){Vec3 at=Vec3.atCenterOf(c.pos);SpellVisuals.observe(level,dev.elementalcircuits.application.observation.SpellVisual.Kind.STEAM,Element.WATER,at,at,1,8);}
            else{
                level.updateNeighborsAt(c.pos,change.after.getBlock());
                if(change.rod)((LightningRodBlock)change.after.getBlock()).onLightningStrike(change.after,level,c.pos);
                if(change.after.is(Blocks.LIGHTNING_ROD))level.updateNeighborsAt(c.pos.relative(change.after.getValue(LightningRodBlock.FACING).getOpposite()),change.after.getBlock());
                if(change.after.is(Blocks.FROSTED_ICE))level.scheduleTick(c.pos,Blocks.FROSTED_ICE,RULES.frostedIceDelay());
            }
            return; // A block receives at most one state transition from this contact snapshot.
        }
    }
}
