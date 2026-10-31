package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.application.observation.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.network.VisualNetwork;
import dev.elementalcircuits.server.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class VisualMinecraftContracts {
    private static void rejects(GameTestHelper h,Runnable r){boolean rejected=false;try{r.run();}catch(RuntimeException e){rejected=true;}h.assertTrue(rejected,"非法特效包拒绝进入观察队列");}
    private static Packet packet(){return new Packet(Mixture.pure(Fixed.ONE).convert(Element.ARCANE,Fixed.ONE),SpellSpec.EMPTY);}
    @GameTest(template="empty") public static void visualNetworkIsBoundedAndRoundTrips(GameTestHelper h){
        var p=new SpellVisual.Point(2,3,4);List<SpellVisual> events=new ArrayList<>();for(var kind:SpellVisual.Kind.values())events.add(new SpellVisual(kind,Element.ARCANE,p,new SpellVisual.Point(0,0,1),2,8));
        var batch=new VisualNetwork.Batch(ResourceLocation.fromNamespaceAndPath("minecraft","overworld"),12,events);var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try{VisualNetwork.write(batch,buf);h.assertTrue(VisualNetwork.read(buf).equals(batch) && buf.readableBytes()==0,"真实网络完整保留维度、世界步、形状和颜色");buf.clear();buf.writeResourceLocation(batch.dimension());buf.writeLong(12);buf.writeVarInt(129);rejects(h,()->VisualNetwork.read(buf));
            buf.clear();buf.writeResourceLocation(batch.dimension());buf.writeLong(12);buf.writeVarInt(1);buf.writeVarInt(500);rejects(h,()->VisualNetwork.read(buf));
        }finally{buf.release();}h.succeed();
    }
    @GameTest(template="empty") public static void longBeamUsesOneObservation(GameTestHelper h){
        var level=h.getLevel();SpellVisuals.unload(level);Vec3 origin=h.absoluteVec(new Vec3(2,8,2));var mana=Mixture.pure(Fixed.ONE).convert(Element.ARCANE,Fixed.ONE);var spec=new SpellSpec(SpellSpec.Method.DIRECTED,SpellSpec.Shape.BEAM,0,64,0,0);
        for(int i=0;i<=64;i+=16)level.getChunkAt(BlockPos.containing(origin.add(0,0,i)));
        SpellEffects.castOwned(level,null,origin,new Vec3(0,0,1),UUID.randomUUID(),new MagicCircuit.Cast(1,mana,spec),RuleResources.defaults(),Fixed.ONE);
        var events=SpellVisuals.pending(level);h.assertTrue(events.size()==2 && events.stream().filter(e->e.kind()==SpellVisual.Kind.BEAM).count()==1 && events.stream().anyMatch(e->e.kind()==SpellVisual.Kind.CAST),"64格光束只有一个形状描述，不再生成257个粒子广播");
        h.assertTrue(events.stream().filter(e->e.kind()==SpellVisual.Kind.BEAM).findFirst().orElseThrow().from().distanceSquared(events.stream().filter(e->e.kind()==SpellVisual.Kind.BEAM).findFirst().orElseThrow().to())>4000,"测量的光束真实跨越64格");
        var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());try{VisualNetwork.write(new VisualNetwork.Batch(level.dimension().location(),0,events),buf);int after=buf.readableBytes();h.assertTrue(after<192,"长光束的实际批次编码含发射观察小于192字节");buf.clear();
            net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket.STREAM_CODEC.encode(buf,new net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket(net.minecraft.core.particles.ParticleTypes.END_ROD,false,origin.x,origin.y,origin.z,.06f,.06f,.06f,.01f,1));int before=buf.readableBytes()*257;
            org.slf4j.LoggerFactory.getLogger("VisualMinecraftContracts").info("VISUAL_NETWORK_PROFILE: old_packets=257 old_payload_bytes={} new_packets=1 new_payload_bytes={}",before,after);
        }finally{buf.release();}
        SpellVisuals.flush(level);h.assertTrue(SpellVisuals.pending(level).isEmpty(),"无人观察时也立即释放本步队列");h.succeed();
    }
    @GameTest(template="empty") public static void shieldAndProjectileHaveShapeObservations(GameTestHelper h){
        var level=h.getLevel();SpellVisuals.unload(level);var spells=new WorldSpells();Vec3 p=h.absoluteVec(new Vec3(2,7,2));spells.launch(null,p,new Vec3(0,0,1),8,1,packet());spells.shield(null,p.add(2,0,0),new Vec3(0,0,1),2,Fixed.ONE);spells.tick(level);
        var events=SpellVisuals.pending(level);h.assertTrue(events.size()==2 && events.stream().anyMatch(e->e.kind()==SpellVisual.Kind.TRAIL) && events.stream().anyMatch(e->e.kind()==SpellVisual.Kind.SHIELD),"飞行真实线段和屏障法线作为两条观察，不逐点画圆");SpellVisuals.unload(level);h.succeed();
    }
    @GameTest(template="empty") public static void saturatedVisualsCannotChangeDamageOrSpellState(GameTestHelper h){
        var level=h.getLevel();SpellVisuals.unload(level);Vec3 origin=h.absoluteVec(new Vec3(2.5,1.5,1.5));var zombie=h.spawn(EntityType.ZOMBIE,new BlockPos(2,1,5));zombie.setNoAi(true);zombie.setInvulnerable(false);
        var cast=new MagicCircuit.Cast(0,packet().mana(),new SpellSpec(SpellSpec.Method.DIRECTED,SpellSpec.Shape.BEAM,0,8,0,0));float before=zombie.getHealth();SpellEffects.castOwned(level,null,origin,new Vec3(0,0,1),new UUID(1,2),cast,RuleResources.defaults(),Fixed.ONE);float damage=before-zombie.getHealth();h.assertTrue(damage>0,"正常视觉队列下造成真实伤害");
        zombie.invulnerableTime=0;for(int i=0;i<VisualBudget.WORLD_LIMIT+1;i++)SpellVisuals.observe(level,SpellVisual.Kind.IMPACT,Element.FIRE,origin,origin,1,1);before=zombie.getHealth();SpellEffects.castOwned(level,null,origin,new Vec3(0,0,1),new UUID(1,2),cast,RuleResources.defaults(),Fixed.ONE);h.assertTrue(Math.abs(before-zombie.getHealth()-damage)<.0001,"特效预算耗尽仍结算完全相同的伤害");
        var one=new WorldSpells();one.launch(null,h.absoluteVec(new Vec3(3,7,2)),new Vec3(0,0,1),8,1,packet());var two=WorldSpells.load(one.save(new CompoundTag(),h.getLevel().registryAccess()));one.tick(level);SpellVisuals.unload(level);two.tick(level);h.assertTrue(one.save(new CompoundTag(),h.getLevel().registryAccess()).equals(two.save(new CompoundTag(),h.getLevel().registryAccess())),"丢弃或保留视觉观察不改变权威弹道现场");SpellVisuals.unload(level);h.succeed();
    }
}
