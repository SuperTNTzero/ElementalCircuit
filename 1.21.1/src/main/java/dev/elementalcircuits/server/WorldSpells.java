package dev.elementalcircuits.server;

import dev.elementalcircuits.adapter.JsonCodecs;
import dev.elementalcircuits.adapter.RadianceResources;
import dev.elementalcircuits.adapter.ProjectileResources;
import dev.elementalcircuits.adapter.SecondaryResources;
import dev.elementalcircuits.domain.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** World-owned in-flight spells and finite stationary shield discs, persisted with chunks/world. */
public final class WorldSpells extends SavedData {
    private record Flight(UUID owner,Vec3 position,Vec3 direction,double remaining,int radius,Packet packet,long strength,int effects,int trail,int bounces,UUID target,ProjectileRules rules,DerivationState derivation,SecondaryRules secondary){ }
    private record Shield(UUID owner,Vec3 position,Vec3 normal,int radius,int life,long capacity){ }
    private final List<Flight> flights=new ArrayList<>();
    private final List<Flight> queued=new ArrayList<>();
    private final List<Shield> shields=new ArrayList<>();
    private boolean invalid;
    private CompoundTag original;
    public static WorldSpells get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(WorldSpells::new,(t,r)->WorldSpells.load(t)),"elementalcircuits_spells");}
    public void launch(UUID owner,Vec3 p,Vec3 direction,int range,int radius,Packet packet){
        launch(owner,p,direction,range,radius,packet,Fixed.ONE);
    }
    public void launch(UUID owner,Vec3 p,Vec3 direction,int range,int radius,Packet packet,long strength){
        if(ShapeVolume.shaped(packet.spell().shape()))throw new IllegalArgumentException("体积形状必须使用区域结算");
        validatePose(p,direction,radius);if(range<1 || range>64)throw new IllegalArgumentException("非法投射射程");
        if(strength<Fixed.ONE/4 || strength>2*Fixed.ONE)throw new IllegalArgumentException("非法弹道强度");
        if(!packet.spell().effects().empty() && !packet.spell().compatibleOutput())throw new IllegalArgumentException("派生效果与投射方式/形状不兼容");
        List<Packet> parts=SpellPayloads.split(packet);if(invalid || flights.size()+queued.size()+parts.size()>256)return;
        ProjectileRules rules=ProjectileResources.rules();
        for(Packet part:parts){Mixture paid=ProjectilePayloads.guidance(part,rules);if(paid.empty())continue;var light=SpellPayloads.illumination(new Packet(paid,part.spell()),strength,RadianceResources.rules());flights.add(new Flight(owner,p,direction,range,part.spell().effects().impactRadius(radius),light.impact(),strength,5,light.ticks(),part.spell().effects().bounces(),null,rules,DerivationState.initial(part.spell().effects()),SecondaryResources.rules()));}setDirty();
    }
    public void beamContact(ServerLevel level,UUID owner,SpellEffects.Trace trace,Vec3 direction,double remaining,int radius,Packet packet,long strength){
        if(invalid)return;var secondary=SecondaryResources.rules();var state=DerivationState.initial(packet.spell().effects());Vec3 at=trace.position().subtract(direction.scale(trace.block()!=null?.03:0));
        var contact=SecondarySpells.contact(level,owner,at,direction,remaining,trace,packet,state,secondary);
        applyContact(level,owner,trace,at,direction,radius,contact.primary(),strength,5,secondary,Set.of(),true);
        for(var child:contact.children())if(flights.size()+queued.size()<256)queued.add(birth(owner,radius,strength,ProjectileResources.rules(),secondary,child));setDirty();
    }
    private static Flight birth(UUID owner,int radius,long strength,ProjectileRules rules,SecondaryRules secondary,SecondarySpells.Birth b){return new Flight(owner,b.position(),b.direction(),b.range(),radius,b.packet(),strength,5,0,0,null,rules,b.state(),secondary);}
    private static void applyContact(ServerLevel level,UUID owner,SpellEffects.Trace trace,Vec3 at,Vec3 direction,int radius,Packet packet,long strength,int effects,SecondaryRules secondary,Set<UUID> excluded,boolean beam){
        if(packet==null)return;
        var mana=SecondaryBudget.hit(packet.mana(),packet.spell().effects()).elemental();
        if(!mana.empty()){
            if(beam && packet.spell().effects().burst()==0)ElementInteractions.beam(level,owner,at.subtract(direction.scale(.01)),trace,mana,strength);
            else SpellEffects.impact(level,owner,at,radius,mana,strength,effects,excluded);
        }
        HitModifiers.apply(level,owner,at,direction,radius,packet,strength,secondary,excluded,trace.entity() instanceof net.minecraft.world.entity.LivingEntity living?living:null,!beam || packet.spell().effects().burst()>0);
    }
    public void shield(UUID owner,Vec3 p,Vec3 normal,int radius,long amount){
        validatePose(p,normal,radius);Fixed.checked(amount);if(amount==0)throw new IllegalArgumentException("屏障容量必须为正");
        if(invalid)return;if(shields.size()>=64)shields.remove(0);
        shields.add(new Shield(owner,p,normal,radius,60,Math.min(Fixed.MAX,amount*8)));setDirty();
    }
    public void tick(ServerLevel level){
        if(invalid || flights.isEmpty() && queued.isEmpty() && shields.isEmpty())return;
        List<Flight> next=new ArrayList<>();int processed=0;boolean changed=!queued.isEmpty();
        for(Flight f:flights){
            processed++;
            // Unloaded regions freeze transport instead of force-loading chunks or deleting the spell.
            if(!level.hasChunkAt(net.minecraft.core.BlockPos.containing(f.position))){next.add(f);continue;}
            var guidance=f.effects>=4?ProjectileGuidance.update(level,f.owner,f.position,f.direction,f.packet.spell().effects().homing(),f.rules.turnDegrees(),f.target,new HashSet<>(f.derivation.visited())):new ProjectileGuidance.Result(f.direction,null);
            Vec3 direction=guidance.direction();double distance=Math.min(0.75,f.remaining);Vec3 end=f.position.add(direction.scale(distance));
            if(!level.hasChunkAt(net.minecraft.core.BlockPos.containing(end))){next.add(f);continue;}
            if(f.effects>=2 && !SpellEffects.loadedBetween(level,f.position,end)){next.add(f);continue;}
            changed=true;
            SpellEffects.Trace trace=SpellEffects.trace(level,f.owner,f.position,end,f.effects>=2,f.effects>=2 && ElementInteractions.hasWind(f.packet.mana()),f.effects>=3 && SpellPayloads.phased(f.packet.mana()),f.effects>=3 && f.packet.mana().values()[Element.SHADOW.component(false)]>0,new HashSet<>(f.derivation.visited()));
            int shieldHit=-1;double nearest=Double.POSITIVE_INFINITY;
            for(int i=0;i<shields.size();i++){
                Shield s=shields.get(i);if(Objects.equals(s.owner,f.owner) || !level.hasChunkAt(net.minecraft.core.BlockPos.containing(s.position)))continue;
                Vec3 delta=trace.position().subtract(f.position);double dot=delta.dot(s.normal);
                if(dot>=-1e-9)continue;double t=s.position.subtract(f.position).dot(s.normal)/dot;
                if(t<0 || t>1)continue;Vec3 hit=f.position.add(delta.scale(t));
                if(hit.distanceToSqr(s.position)<=s.radius*s.radius && t<nearest){nearest=t;shieldHit=i;}
            }
            Packet impactPacket=f.packet;
            if(shieldHit>=0){Shield s=shields.get(shieldHit);long incomingPower=Math.max(1,f.packet.mana().total()*f.strength/Fixed.ONE);long left=s.capacity-incomingPower;
                if(left<=0)shields.remove(shieldHit);else shields.set(shieldHit,new Shield(s.owner,s.position,s.normal,s.radius,s.life,left));
                SpellEffects.contactFeedback(level,f.position,Element.ARCANE,8);
                if(left>=0)continue;
                long absorbed=Math.min(f.packet.mana().total(),s.capacity*Fixed.ONE/f.strength);
                impactPacket=new Packet(f.packet.mana().minus(f.packet.mana().take(absorbed)),f.packet.spell());
            }
            SpellVisuals.observe(level,dev.elementalcircuits.application.observation.SpellVisual.Kind.TRAIL,impactPacket.mana().dominant(),f.position,trace.position(),1,1);
            Vec3 at=trace.position().subtract(direction.scale(trace.blocked()?0.03:0));
            if(trace.blocked())SpellSounds.observe(level,dev.elementalcircuits.application.observation.SpellSound.Kind.IMPACT,impactPacket.mana().dominant(),trace.position());
            double traveled=f.position.distanceTo(trace.position());
            if(f.effects>=4 && f.bounces>0 && trace.block()!=null && traveled+.03<f.remaining && !level.getBlockState(trace.block().getBlockPos()).getCollisionShape(level,trace.block().getBlockPos()).isEmpty()){
                Mixture retained=ProjectilePayloads.bounce(impactPacket.mana(),f.rules);
                if(!retained.empty()){
                    var face=trace.block().getDirection();Vec3 normal=new Vec3(face.getStepX(),face.getStepY(),face.getStepZ());Vec3 reflected=direction.subtract(normal.scale(2*direction.dot(normal))).normalize();
                    Vec3 outside=trace.position().add(normal.scale(.03));
                    next.add(new Flight(f.owner,outside,reflected,f.remaining-traveled-.03,f.radius,new Packet(retained,impactPacket.spell()),f.strength,f.effects,Math.max(0,f.trail-1),f.bounces-1,guidance.target(),f.rules,f.derivation,f.secondary));
                }
                continue;
            }
            if(f.trail>0)WorldIllumination.get(level).glow(level,f.owner,net.minecraft.core.BlockPos.containing(at),RadianceResources.rules().trailLife());
            if(trace.blocked() || f.remaining<=distance){
                if(f.effects>=5){
                    var contact=SecondarySpells.contact(level,f.owner,at,direction,Math.max(0,f.remaining-traveled),trace,impactPacket,f.derivation,f.secondary);
                    applyContact(level,f.owner,trace,at,direction,f.radius,contact.primary(),f.strength,f.effects,f.secondary,new HashSet<>(f.derivation.visited()),false);
                    for(var child:contact.children())if(next.size()+flights.size()-processed+queued.size()<256)next.add(birth(f.owner,f.radius,f.strength,f.rules,f.secondary,child));
                }else SpellEffects.impact(level,f.owner,at,f.radius,impactPacket.mana(),f.strength,f.effects);
            }
            else next.add(new Flight(f.owner,end,direction,f.remaining-distance,f.radius,impactPacket,f.strength,f.effects,Math.max(0,f.trail-1),f.bounces,guidance.target(),f.rules,f.derivation,f.secondary));
        }
        flights.clear();flights.addAll(next);flights.addAll(queued);queued.clear();
        for(int i=shields.size()-1;i>=0;i--){Shield s=shields.get(i);
            if(!level.hasChunkAt(net.minecraft.core.BlockPos.containing(s.position)))continue;
            changed=true;
            if(s.life<=1){shields.remove(i);continue;}
            shields.set(i,new Shield(s.owner,s.position,s.normal,s.radius,s.life-1,s.capacity));
            SpellVisuals.observe(level,dev.elementalcircuits.application.observation.SpellVisual.Kind.SHIELD,Element.ARCANE,s.position,s.normal,s.radius,1);
        }
        if(changed)setDirty();
    }
    private static void validatePose(Vec3 position,Vec3 direction,int radius){
        if(position==null || direction==null || !Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
            || Math.abs(position.x)>30000100 || Math.abs(position.y)>30000100 || Math.abs(position.z)>30000100
            || !Double.isFinite(direction.length()) || Math.abs(direction.length()-1)>0.0001 || radius<1 || radius>8)throw new IllegalArgumentException("非法显化位置、方向或半径");
    }
    private static void owner(CompoundTag tag){if(tag.contains("Owner") && !tag.hasUUID("Owner"))throw new IllegalArgumentException("施法所有者身份损坏");}
    private static void bool(CompoundTag tag,String key){if(!tag.contains(key,Tag.TAG_BYTE) || tag.getByte(key)<0 || tag.getByte(key)>1)throw new IllegalArgumentException("布尔状态损坏："+key);}
    private static ListTag compounds(CompoundTag tag,String key){
        if(!tag.contains(key,Tag.TAG_LIST))throw new IllegalArgumentException("缺少状态列表："+key);
        ListTag list=(ListTag)tag.get(key);if(list.getElementType()!=Tag.TAG_END && list.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("状态列表类型错误："+key);return list;
    }
    private static void vector(CompoundTag t,String key,Vec3 v){t.putDouble(key+"X",v.x);t.putDouble(key+"Y",v.y);t.putDouble(key+"Z",v.z);}
    private static Vec3 vector(CompoundTag t,String key){
        for(String axis:List.of("X","Y","Z"))if(!t.contains(key+axis,Tag.TAG_DOUBLE))throw new IllegalArgumentException("缺少坐标");
        Vec3 v=new Vec3(t.getDouble(key+"X"),t.getDouble(key+"Y"),t.getDouble(key+"Z"));
        if(!Double.isFinite(v.x)||!Double.isFinite(v.y)||!Double.isFinite(v.z)||Math.abs(v.x)>30000100||Math.abs(v.z)>30000100||Math.abs(v.y)>30000100)throw new IllegalArgumentException("非法坐标");return v;
    }
    @Override public CompoundTag save(CompoundTag root,net.minecraft.core.HolderLookup.Provider registries){
        if(invalid)return original.copy();
        root.putInt("Version",7);ListTag fList=new ListTag(),sList=new ListTag();
        for(var batch:List.of(flights,queued))for(Flight f:batch){CompoundTag t=new CompoundTag();t.putBoolean("Queued",batch==queued);if(f.owner!=null)t.putUUID("Owner",f.owner);vector(t,"P",f.position);vector(t,"D",f.direction);t.putDouble("Remaining",f.remaining);t.putInt("Radius",f.radius);t.putString("Packet",JsonCodecs.packetText(f.packet));t.putLong("Strength",f.strength);t.putInt("Effects",f.effects);t.putInt("Trail",f.trail);t.putInt("Bounces",f.bounces);if(f.target!=null)t.putUUID("Target",f.target);t.putString("ProjectileRules",JsonCodecs.projectileRules(f.rules));t.putString("SecondaryRules",JsonCodecs.secondaryRules(f.secondary));
            t.putBoolean("SplitReady",f.derivation.splitReady());t.putInt("ChainLeft",f.derivation.chainLeft());ListTag visited=new ListTag();for(UUID id:f.derivation.visited())visited.add(NbtUtils.createUUID(id));t.put("Visited",visited);fList.add(t);}
        for(Shield s:shields){CompoundTag t=new CompoundTag();if(s.owner!=null)t.putUUID("Owner",s.owner);vector(t,"P",s.position);vector(t,"N",s.normal);t.putInt("Radius",s.radius);t.putInt("Life",s.life);t.putLong("Capacity",s.capacity);sList.add(t);}
        root.put("Flights",fList);root.put("Shields",sList);return root;
    }
    public static WorldSpells load(CompoundTag root){
        WorldSpells state=new WorldSpells();
        try{
            if(!root.contains("Version",Tag.TAG_INT))throw new IllegalArgumentException("版本类型损坏");
            int version=root.getInt("Version");
            if(version<1 || version>7 || !root.contains("Flights",Tag.TAG_LIST) || !root.contains("Shields",Tag.TAG_LIST))throw new IllegalArgumentException("未知世界法术版本");
            ListTag fList=compounds(root,"Flights"),sList=compounds(root,"Shields");
            if(fList.size()>256 || sList.size()>64)throw new IllegalArgumentException("过多世界法术");
            for(int i=0;i<fList.size();i++){
                CompoundTag t=fList.getCompound(i);owner(t);
                if(!t.contains("Remaining",Tag.TAG_DOUBLE) || !t.contains("Radius",Tag.TAG_INT) || !t.contains("Packet",Tag.TAG_STRING))throw new IllegalArgumentException("弹道字段类型损坏");
                Vec3 d=vector(t,"D");double remaining=t.getDouble("Remaining");int radius=t.getInt("Radius");
                if(Math.abs(d.length()-1)>0.0001 || !Double.isFinite(remaining)||remaining<=0||remaining>64||radius<1||radius>8)throw new IllegalArgumentException("非法弹道");
                long strength=version==1?Fixed.ONE:t.getLong("Strength");if(version>=2 && !t.contains("Strength",Tag.TAG_LONG) || strength<Fixed.ONE/4 || strength>2*Fixed.ONE)throw new IllegalArgumentException("非法弹道强度");
                int effects=version<3?1:t.getInt("Effects");if(version>=3 && !t.contains("Effects",Tag.TAG_INT) || effects<1 || effects>(version<4?2:version<5?3:version<6?4:5))throw new IllegalArgumentException("非法世界作用版本");
                int trail=version<4?0:t.getInt("Trail");if(version>=4 && !t.contains("Trail",Tag.TAG_INT) || trail<0 || trail>128 || effects<3 && trail!=0)throw new IllegalArgumentException("非法弹道照明");
                Packet packet=Objects.requireNonNull(JsonCodecs.readPacket(t.getString("Packet")));
                if(ShapeVolume.shaped(packet.spell().shape()))throw new IllegalArgumentException("旧弹道记录不能携带区域形状");
                int bounces=version<5?0:t.getInt("Bounces");
                ProjectileRules rules=version<5?ProjectileRules.protocolV1():JsonCodecs.readProjectileRules(t.getString("ProjectileRules"));
                UUID target=version<5 || !t.hasUUID("Target")?null:t.getUUID("Target");
                if(version>=5 && !t.contains("Bounces",Tag.TAG_INT) || bounces<0 || bounces>packet.spell().effects().bounces() || effects<4 && (!packet.spell().effects().empty() || bounces!=0 || target!=null) || target!=null && packet.spell().effects().homing()==0 || !packet.spell().effects().empty() && !packet.spell().compatibleOutput() || version>=5 && t.contains("Target") && !t.hasUUID("Target"))throw new IllegalArgumentException("非法投射物派生现场");
                SecondaryRules secondary=version<6?SecondaryRules.protocolV1():JsonCodecs.readSecondaryRules(t.getString("SecondaryRules"));
                DerivationState derivation=DerivationState.NONE;
                if(version>=6){bool(t,"SplitReady");bool(t,"Queued");if(!t.contains("SplitReady",Tag.TAG_BYTE) || !t.contains("ChainLeft",Tag.TAG_INT) || !t.contains("Visited",Tag.TAG_LIST))throw new IllegalArgumentException("缺少派生现场");
                    ListTag ids=t.getList("Visited",Tag.TAG_INT_ARRAY);if(ids.size()!=((ListTag)t.get("Visited")).size())throw new IllegalArgumentException("错误命中名单类型");List<UUID> visited=new ArrayList<>();for(Tag id:ids)visited.add(NbtUtils.loadUUID(id));derivation=new DerivationState(t.getBoolean("SplitReady"),t.getInt("ChainLeft"),visited);derivation.validate(packet.spell().effects());
                }
                if(effects<5 && (packet.spell().effects().secondary() || packet.spell().effects().hit() || !derivation.equals(DerivationState.NONE)))throw new IllegalArgumentException("旧作用协议不能携带新派生");
                if(version>=6 && !t.contains("Queued",Tag.TAG_BYTE))throw new IllegalArgumentException("缺少子代队列边界");
                var destination=version>=6 && t.getBoolean("Queued")?state.queued:state.flights;
                destination.add(new Flight(t.hasUUID("Owner")?t.getUUID("Owner"):null,vector(t,"P"),d,remaining,radius,packet,strength,effects,trail,bounces,target,rules,derivation,secondary));
            }
            for(int i=0;i<sList.size();i++){
                CompoundTag t=sList.getCompound(i);owner(t);
                if(!t.contains("Life",Tag.TAG_INT) || !t.contains("Radius",Tag.TAG_INT) || !t.contains("Capacity",Tag.TAG_LONG))throw new IllegalArgumentException("屏障字段类型损坏");
                Vec3 n=vector(t,"N");int life=t.getInt("Life"),radius=t.getInt("Radius");long cap=t.getLong("Capacity");
                if(Math.abs(n.length()-1)>0.0001||life<1||life>60||radius<1||radius>8||cap<1||cap>Fixed.MAX)throw new IllegalArgumentException("非法屏障");
                state.shields.add(new Shield(t.hasUUID("Owner")?t.getUUID("Owner"):null,vector(t,"P"),n,radius,life,cap));
            }
        }catch(RuntimeException e){state.flights.clear();state.queued.clear();state.shields.clear();state.invalid=true;state.original=root.copy();org.slf4j.LoggerFactory.getLogger("ElementalCircuits").error("世界法术存档校验失败，保留原始数据并停用：{}",e.getMessage());}
        return state;
    }
}
