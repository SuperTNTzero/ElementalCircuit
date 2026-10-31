package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.application.observation.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.server.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class AudioMinecraftContracts {
    private static MagicCircuit.Cast cast(SpellSpec.Shape shape){return new MagicCircuit.Cast(0,Mixture.pure(Fixed.ONE).convert(Element.EARTH,Fixed.ONE),new SpellSpec(SpellSpec.Method.DIRECTED,shape,0,shape==SpellSpec.Shape.SHIELD?0:8,shape==SpellSpec.Shape.SHIELD?1:0,0));}
    @GameTest(template="empty") public static void startStopAndEmptyOutputHaveCorrectAudio(GameTestHelper h){
        var level=h.getLevel();SpellSounds.unload(level);
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"audio-test"));
        var wand=new net.minecraft.world.item.ItemStack(ElementalCircuits.WAND.get());p.getInventory().selected=0;p.getInventory().setItem(0,wand);
        var a=CarrierService.assembly(wand);a.replace(1,AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.OUTPUT));
        CarrierCommands.start(p,wand);h.assertTrue(a.circuit(1).powered() && SpellSounds.pending(level).size()==1 && SpellSounds.pending(level).get(0).kind()==SpellSound.Kind.START,"成功起阵产生起动声");SpellSounds.flush(level);
        CarrierCommands.start(p,wand);h.assertTrue(!a.circuit(1).powered() && SpellSounds.pending(level).isEmpty(),"停止供给不误播起动声");
        a.replace(1,new Blueprint(1,RuleResources.defaults(),List.of(),List.of(),false));CarrierCommands.start(p,wand);
        h.assertTrue(!a.circuit(1).powered() && SpellSounds.pending(level).isEmpty(),"拒绝的空法阵起动保持静默");
        SpellEffects.castOwned(level,null,h.absoluteVec(new Vec3(2,5,2)),new Vec3(0,0,1),new UUID(1,2),new MagicCircuit.Cast(0,Mixture.pure(Fixed.ONE),SpellSpec.EMPTY),RuleResources.defaults(),Fixed.ONE);
        h.assertTrue(SpellSounds.pending(level).isEmpty(),"无元素的空输出不产生发射声");SpellSounds.unload(level);h.succeed();
    }
    @GameTest(template="empty") public static void nativeAudioPacketRoundTrips(GameTestHelper h){
        for(var kind:SpellSound.Kind.values())for(var element:Element.values()){
            var s=new SpellSound(kind,element,new SpellVisual.Point(1,2,3));var packet=SpellSounds.packet(s,42);var buf=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try{packet.write(buf);var decoded=new ClientboundSoundPacket(buf);h.assertTrue(decoded.getSound().value().getLocation().equals(SpellSounds.event(s).getLocation()) && decoded.getSource()==SoundSource.PLAYERS && decoded.getX()==1 && decoded.getY()==2 && decoded.getZ()==3 && decoded.getVolume()==AudioResources.policy().volumes().get(kind) && decoded.getPitch()>=.96f && decoded.getPitch()<=1.04f && decoded.getSeed()==packet.getSeed(),"原版声音网络保留元素、位置、音量及种子");}finally{buf.release();}
        }h.succeed();
    }
    @GameTest(template="empty") public static void shieldIsAudibleOnlyWhenCreated(GameTestHelper h){
        var level=h.getLevel();SpellSounds.unload(level);Vec3 p=h.absoluteVec(new Vec3(2,5,2));
        SpellEffects.castOwned(level,null,p,new Vec3(0,0,1),new UUID(1,2),cast(SpellSpec.Shape.SHIELD),RuleResources.defaults(),Fixed.ONE);
        h.assertTrue(SpellSounds.pending(level).size()==1 && SpellSounds.pending(level).get(0).kind()==SpellSound.Kind.SHIELD,"展开产生一次屏障声");SpellSounds.flush(level);
        WorldSpells.get(level).tick(level);h.assertTrue(SpellSounds.pending(level).isEmpty(),"持续屏障刷新不产生循环声音");SpellSounds.unload(level);h.succeed();
    }
    @GameTest(template="empty") public static void blockImpactAndSaturationAreCosmetic(GameTestHelper h){
        var level=h.getLevel();SpellSounds.unload(level);Vec3 p=h.absoluteVec(new Vec3(2.5,1.5,1.5));h.setBlock(new BlockPos(2,1,5),Blocks.STONE);
        SpellEffects.castOwned(level,null,p,new Vec3(0,0,1),new UUID(1,2),cast(SpellSpec.Shape.BEAM),RuleResources.defaults(),Fixed.ONE);
        var events=SpellSounds.pending(level);h.assertTrue(events.stream().anyMatch(s->s.kind()==SpellSound.Kind.CAST) && events.stream().anyMatch(s->s.kind()==SpellSound.Kind.IMPACT),"无实体的方块命中也产生发射和接触声");
        SpellSounds.unload(level);var zombie=h.spawn(EntityType.ZOMBIE,new BlockPos(2,1,3));zombie.setNoAi(true);zombie.setInvulnerable(false);float before=zombie.getHealth();
        SpellEffects.castOwned(level,null,p,new Vec3(0,0,1),new UUID(1,2),cast(SpellSpec.Shape.BEAM),RuleResources.defaults(),Fixed.ONE);float damage=before-zombie.getHealth();h.assertTrue(damage>0,"正常音效下有真实伤害");
        for(int i=0;i<AudioResources.policy().worldLimit()+2;i++)SpellSounds.observe(level,SpellSound.Kind.IMPACT,Element.FIRE,p.add(i*4,0,0));
        zombie.invulnerableTime=0;before=zombie.getHealth();SpellEffects.castOwned(level,null,p,new Vec3(0,0,1),new UUID(1,2),cast(SpellSpec.Shape.BEAM),RuleResources.defaults(),Fixed.ONE);
        h.assertTrue(Math.abs(before-zombie.getHealth()-damage)<.0001,"声音过载和去重不改变权威伤害");
        var one=new WorldSpells();one.launch(null,p.add(1,5,0),new Vec3(0,0,1),8,1,new Packet(cast(SpellSpec.Shape.SPHERE).mana(),cast(SpellSpec.Shape.SPHERE).spell()));var two=WorldSpells.load(one.save(new CompoundTag()));one.tick(level);SpellSounds.unload(level);two.tick(level);
        h.assertTrue(one.save(new CompoundTag()).equals(two.save(new CompoundTag())),"声音队列不进入世界法术存档");SpellSounds.unload(level);h.succeed();
    }
}
