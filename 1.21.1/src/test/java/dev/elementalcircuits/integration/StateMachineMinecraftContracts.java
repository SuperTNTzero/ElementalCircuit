package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.network.CircuitNetwork;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class StateMachineMinecraftContracts {
    private static ServerPlayer player(GameTestHelper h){return net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"state-test"));}
    private static Packet packet(){return new Packet(Mixture.pure(Fixed.ONE).convert(Element.FIRE,Fixed.ONE),SpellSpec.EMPTY);}
    private static void rejects(GameTestHelper h,Runnable action,String message){boolean rejected=false;try{action.run();}catch(RuntimeException e){rejected=true;}h.assertTrue(rejected,message);}
    @GameTest(template="empty") public static void ownershipIsRevalidatedAtWorldBoundary(GameTestHelper h){
        var previous=player(h);var next=player(h);var wand=new ItemStack(ElementalCircuits.WAND.get());previous.getInventory().setItem(0,wand);var a=CarrierRepository.load(wand);a.replace(1,AssemblyContracts.line(true,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.OUTPUT));a.circuit(1).start();
        long oldMana=PlayerMana.get(previous).available(),newMana=PlayerMana.get(next).available();WorldCircuits.enqueue(wand,previous);previous.getInventory().setItem(0,ItemStack.EMPTY);next.getInventory().setItem(0,wand);WorldCircuits.tick(h.getLevel());
        h.assertTrue(!a.powered() && a.circuit(1).snapshot().clock()==0 && PlayerMana.get(previous).available()==oldMana && PlayerMana.get(next).available()==newMana,"转移后的排队任务不能远程扣前所有者魔力或施法");
        WorldCircuits.enqueue(wand,next);WorldCircuits.tick(h.getLevel());h.assertTrue(!a.powered() && a.circuit(1).snapshot().clock()==0,"新所有者未手动起阵不得自动继续供给");
        a.circuit(1).start();WorldCircuits.enqueue(wand,next);WorldCircuits.tick(h.getLevel());h.assertTrue(PlayerMana.get(next).available()==newMana-4*Fixed.ONE && PlayerMana.get(previous).available()==oldMana,"明确重启后只使用当前所有者供给");h.succeed();
    }
    @GameTest(template="empty") public static void invalidSpellStateIsPreservedAndDisabled(GameTestHelper h){
        var valid=new WorldSpells();valid.launch(UUID.randomUUID(),h.absoluteVec(new Vec3(2,3,2)),new Vec3(0,0,1),8,1,packet());valid.shield(UUID.randomUUID(),h.absoluteVec(new Vec3(4,3,4)),new Vec3(0,0,1),2,Fixed.ONE);var saved=valid.save(new CompoundTag(),h.getLevel().registryAccess());
        List<Consumer<CompoundTag>> mutations=List.of(
            root->{var list=new ListTag();list.add(StringTag.valueOf("bad-flight"));root.put("Flights",list);},
            root->root.getList("Flights",10).getCompound(0).putString("Owner","invalid-owner"),
            root->root.getList("Flights",10).getCompound(0).putDouble("Radius",1.5),
            root->root.getList("Flights",10).getCompound(0).putByte("Queued",(byte)2),
            root->root.getList("Shields",10).getCompound(0).putDouble("Life",4.5),
            root->root.getList("Shields",10).getCompound(0).putString("Owner","invalid-owner"),
            root->root.putLong("Version",7));
        for(var mutation:mutations){var raw=saved.copy();mutation.accept(raw);var loaded=WorldSpells.load(raw);loaded.tick(h.getLevel());loaded.launch(null,h.absoluteVec(new Vec3(2,3,2)),new Vec3(0,0,1),8,1,packet());h.assertTrue(loaded.save(new CompoundTag(),h.getLevel().registryAccess()).equals(raw),"非法状态保持原NBT，整体停用，不降级匿名所有者或吞掉队列");}
        h.succeed();
    }
    @GameTest(template="empty") public static void invalidBirthIsRejectedBeforeMutation(GameTestHelper h){
        var state=new WorldSpells();var before=state.save(new CompoundTag(),h.getLevel().registryAccess());Vec3 p=h.absoluteVec(new Vec3(2,3,2));
        rejects(h,()->state.launch(null,p,new Vec3(0,0,1),0,1,packet()),"拒绝零射程弹道");
        rejects(h,()->state.launch(null,p,Vec3.ZERO,8,1,packet()),"拒绝无方向弹道");
        rejects(h,()->state.launch(null,new Vec3(Double.NaN,1,1),new Vec3(0,0,1),8,1,packet()),"拒绝NaN出生位置");
        rejects(h,()->state.shield(null,p,new Vec3(0,0,1),1,0),"拒绝零容量屏障");
        rejects(h,()->state.shield(null,p,new Vec3(0,0,1),1,Long.MAX_VALUE),"拒绝溢出屏障容量");
        h.assertTrue(state.save(new CompoundTag(),h.getLevel().registryAccess()).equals(before),"所有非法出生都不修改队列或已有状态");h.succeed();
    }
    @GameTest(template="empty") public static void fixedCarrierMetadataCannotLoseOwnershipOrCharge(GameTestHelper h){
        var pos=h.absolutePos(new BlockPos(2,2,2));var state=ElementalCircuits.CIRCUIT_BLOCK.get().defaultBlockState();var block=new dev.elementalcircuits.block.CircuitBlockEntity(pos,state);block.placed(new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get()),player(h));var saved=block.saveWithFullMetadata(h.getLevel().registryAccess());
        for(int i=0;i<3;i++){
            var raw=saved.copy();var root=raw.getCompound("Carrier").getCompound("components").getCompound("minecraft:custom_data");if(i==0)root.putString("FixedCircuit","bad-root");else if(i==1)root.getCompound("FixedCircuit").putString("Owner","bad-owner");else root.getCompound("FixedCircuit").putDouble("Charge",1.5);
            var restored=new dev.elementalcircuits.block.CircuitBlockEntity(pos,state);restored.loadWithComponents(raw,h.getLevel().registryAccess());rejects(h,restored::owner,"损坏的固定元数据整体停用，不降级所有者");rejects(h,restored::available,"损坏的供能不按空储量重建");h.assertTrue(restored.saveWithFullMetadata(h.getLevel().registryAccess()).equals(raw),"坏根、所有者、计数均保留原始NBT");
            var target=new dev.elementalcircuits.block.CircuitBlockEntity(pos,state);var before=target.saveWithFullMetadata(h.getLevel().registryAccess());rejects(h,()->target.placed(ItemStack.parseOptional(h.getLevel().registryAccess(),(CompoundTag)raw.getCompound("Carrier")),player(h)),"放置拒绝坏所有者、供能或根记录");h.assertTrue(target.saveWithFullMetadata(h.getLevel().registryAccess()).equals(before),"放置校验失败保持原载体");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void unloadedSpellsRemainCleanAndFrozen(GameTestHelper h){
        var state=new WorldSpells();var p=new Vec3(-2300000,100,-2300000);h.assertTrue(!h.getLevel().hasChunkAt(BlockPos.containing(p)),"冻结测试位置未加载");state.launch(null,p,new Vec3(0,0,1),8,1,packet());state.shield(UUID.randomUUID(),p,new Vec3(0,0,-1),2,Fixed.ONE);var before=state.save(new CompoundTag(),h.getLevel().registryAccess());state.setDirty(false);
        for(int i=0;i<10;i++)state.tick(h.getLevel());h.assertTrue(!state.isDirty() && state.save(new CompoundTag(),h.getLevel().registryAccess()).equals(before),"冻结弹道和屏障不走寿命、不修改状态、不反复标脏");h.succeed();
    }
    @GameTest(template="empty") public static void unloadedShieldCannotInterceptLoadedFlight(GameTestHelper h){
        int chunk=180000,x=chunk*16,z=chunk*16;h.getLevel().getChunk(chunk,chunk);Vec3 center=new Vec3(x+16.25,100,z+6.5);h.assertTrue(!h.getLevel().hasChunkAt(BlockPos.containing(center)),"屏障中心在未加载相邻区块");
        var state=new WorldSpells();state.shield(new UUID(9,1),center,new Vec3(0,0,-1),2,Fixed.ONE);state.launch(new UUID(9,2),new Vec3(x+15.5,100,z+6),new Vec3(0,0,1),8,1,packet());state.tick(h.getLevel());var saved=state.save(new CompoundTag(),h.getLevel().registryAccess());
        h.assertTrue(saved.getList("Flights",10).size()==1 && saved.getList("Shields",10).getCompound(0).getInt("Life")==60,"未加载的冻结屏障不参与已加载弹道碰撞");h.succeed();
    }
    @GameTest(template="empty") public static void requestTokensSurviveRealNetworkEncoding(GameTestHelper h){
        UUID identity=UUID.randomUUID(),token=UUID.randomUUID();var request=new CircuitNetwork.Request(identity,2,4,"BRASS",token);var reply=new CircuitNetwork.Editor(identity,JsonCodecs.blueprint(Blueprint.empty(RuleResources.defaults())),false,0,false,"已完成",WandResources.definitions().basic(),0,2,false,"","",false,token);
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{CircuitNetwork.writeRequest(request,buffer);CircuitNetwork.writeEditor(reply,buffer);h.assertTrue(CircuitNetwork.readRequest(buffer).equals(request) && CircuitNetwork.readEditor(buffer).equals(reply) && buffer.readableBytes()==0,"真实传输保留目标、动作和关联令牌");}finally{buffer.release();}h.succeed();
    }
}
