package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.block.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.server.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.io.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class RuntimeBoundaryMinecraftContracts {
    private static ServerPlayer player(GameTestHelper h){return net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"runtime-boundary"));}
    private static Blueprint dense(boolean wires){
        List<Rune> nodes=new ArrayList<>();for(int q=-6;q<=6;q++)for(int r=-6;r<=6;r++)if(new Hex(q,r).inside(6) && nodes.size()<64)nodes.add(Rune.create(nodes.size()+1,wires?Rune.Kind.OR:Rune.Kind.SIGNAL,new Hex(q,r)));
        List<Route> routes=new ArrayList<>();if(wires)for(var a:nodes)for(var b:nodes)if(routes.size()<64 && a.cell().distance(b.cell())==1)routes.add(new Route(a.id(),b.id(),Route.Payload.SIGNAL,List.of(a.cell(),b.cell())));
        return new Blueprint(1,RuleResources.defaults().withMaterial(Rules.Material.BRASS),nodes,routes,true);
    }
    private static CompoundTag binary(CompoundTag tag)throws IOException{var bytes=new ByteArrayOutputStream();NbtIo.writeCompressed(tag,bytes);return NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()));}
    private static CircuitBlockEntity fixed(GameTestHelper h,BlockPos pos,ServerPlayer p){h.setBlock(pos,ElementalCircuits.CIRCUIT_BLOCK.get().defaultBlockState());var b=(CircuitBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos));b.placed(new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get()),p);WorldCircuits.register(b);return b;}
    private static void rejects(GameTestHelper h,Runnable action,String message){boolean rejected=false;try{action.run();}catch(RuntimeException e){rejected=true;}h.assertTrue(rejected,message);}
    @GameTest(template="empty") public static void largeNotebookBinaryAndNetwork(GameTestHelper h)throws Exception{
        var note=new ItemStack(ElementalCircuits.NOTEBOOK.get());var v=NotebookRepository.load(note);for(int i=0;i<16;i++)v=v.add("火水法阵 "+i,dense(false));NotebookRepository.save(note,v);
        String expected=JsonCodecs.volume(v);h.assertTrue(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65535,"合法蓝图簿确实超过旧StringTag写入上限");
        var copy=ItemStack.of(binary(note.save(new CompoundTag())));h.assertTrue(NotebookRepository.load(copy).equals(v),"真实压缩NBT文件字节往返保留16页");
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{buffer.writeItem(note);var client=buffer.readItem();h.assertTrue(NotebookRepository.load(client).equals(v),"真实物品网络字节往返保留16页");}finally{buffer.release();}
        h.succeed();
    }
    @GameTest(template="empty") public static void largeAssemblyBinaryAndNetwork(GameTestHelper h)throws Exception{
        var book=new ItemStack(ElementalCircuits.SPELL_BOOK.get());var a=CarrierRepository.load(book);var blueprint=dense(true);a.replace(1,blueprint);for(int i=0;i<3;i++){int slot=a.add(blueprint.rules());a.replace(slot,blueprint);}a.select(3);
        var mana=new ManaPool(64*Fixed.ONE,0,0);for(int slot:a.slots()){a.circuit(slot).start();a.circuit(slot).tick(mana);}CarrierRepository.commit(book);
        String expected=JsonCodecs.assembly(a);h.assertTrue(expected.length()>65535,"四槽有线法阵超过旧字符串上限");var copy=ItemStack.of(binary(book.save(new CompoundTag())));
        h.assertTrue(JsonCodecs.assembly(CarrierRepository.load(copy)).equals(expected),"磁盘字节完整恢复四槽活动状态和选择");
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{buffer.writeItem(book);var client=buffer.readItem();h.assertTrue(JsonCodecs.assembly(CarrierRepository.load(client)).equals(expected),"网络字节完整恢复现场");}finally{buffer.release();}h.succeed();
    }
    @GameTest(template="empty") public static void legacyMigrationAndCorruption(GameTestHelper h){
        var old=new ItemStack(ElementalCircuits.WAND.get());UUID id=UUID.randomUUID();var a=new CircuitAssembly(id,AssemblyResources.rules(),new TickCircuit(AssemblyContracts.line(true,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.OUTPUT)));a.circuit(1).start();a.circuit(1).tick(new ManaPool(64*Fixed.ONE,0,0));var t=old.getOrCreateTagElement("ElementalCircuit");t.putUUID("Identity",id);t.putString("Assembly",JsonCodecs.assembly(a));
        h.assertTrue(JsonCodecs.assembly(CarrierRepository.load(old)).equals(JsonCodecs.assembly(a)) && t.contains("Assembly",Tag.TAG_COMPOUND),"旧完整现场经验证迁移，不重置时钟或魔力");
        var note=new ItemStack(ElementalCircuits.NOTEBOOK.get());var volume=BlueprintVolume.empty(UUID.randomUUID()).add("历史中文",dense(false));note.getOrCreateTag().putString("BlueprintVolume",JsonCodecs.volume(volume));h.assertTrue(NotebookRepository.load(note).equals(volume) && note.getTag().contains("BlueprintVolume",Tag.TAG_COMPOUND),"旧蓝图簿显式迁移");
        var bad=new ItemStack(ElementalCircuits.WAND.get());var raw=bad.getOrCreateTagElement("ElementalCircuit");raw.putUUID("Identity",UUID.randomUUID());var data=new CompoundTag();data.putInt("Format",1);data.putByteArray("Utf8",new byte[]{(byte)0xc3,(byte)0x28});raw.put("Assembly",data);var before=bad.save(new CompoundTag()).copy();rejects(h,()->CarrierRepository.load(bad),"拒绝损坏UTF-8");h.assertTrue(bad.save(new CompoundTag()).equals(before),"失败不覆盖原始字节");
        data=new CompoundTag();data.putInt("Format",42);data.putByteArray("Utf8",new byte[0]);raw.put("Assembly",data);rejects(h,()->CarrierRepository.load(bad),"拒绝未知存储版本");
        rejects(h,()->JsonNbt.encode("x".repeat(1048577),1048576),"写入前校验真实字节上限");
        // Replacing the NBT tag after cache creation cannot conceal corrupt replacement state.
        t.putString("Assembly","损坏记录");rejects(h,()->CarrierRepository.load(old),"替换原始标签会失效缓存");h.assertTrue(t.getString("Assembly").equals("损坏记录"),"缓存失效不以旧缓存覆盖损坏记录");
        var badRoot=new ItemStack(ElementalCircuits.WAND.get());badRoot.getOrCreateTag().putString("ElementalCircuit","原始坏根记录");rejects(h,()->CarrierRepository.load(badRoot),"拒绝非复合根记录");CarrierFaults.report(badRoot,null);h.assertTrue(badRoot.getTag().getString("ElementalCircuit").equals("原始坏根记录"),"故障标记不覆盖坏根记录");
        var missingIdentity=new ItemStack(ElementalCircuits.WAND.get());missingIdentity.getOrCreateTagElement("ElementalCircuit").putString("Assembly",JsonCodecs.assembly(a));var untouched=missingIdentity.save(new CompoundTag()).copy();rejects(h,()->CarrierRepository.load(missingIdentity),"拒绝缺失身份的完整现场");h.assertTrue(missingIdentity.save(new CompoundTag()).equals(untouched),"拒绝时不分配随机新身份");h.succeed();
    }
    @GameTest(template="empty") public static void idleCommitsAndNestedMutations(GameTestHelper h){
        var p=player(h);var wand=new ItemStack(ElementalCircuits.WAND.get());p.getInventory().setItem(0,wand);var a=CarrierRepository.load(wand);a.replace(1,dense(false));CarrierRepository.commit(wand);Tag saved=wand.getTag().getCompound("ElementalCircuit").get("Assembly");
        for(int i=0;i<40;i++){WorldCircuits.enqueue(wand,p);WorldCircuits.tick(h.getLevel());}
        h.assertTrue(wand.getTag().getCompound("ElementalCircuit").get("Assembly")==saved && !CarrierRepository.commit(wand),"待机40步不序列化、不替换NBT标签");
        a.circuit(1).start();h.assertTrue(CarrierRepository.commit(wand),"直接改变嵌套VM也触发提交");saved=wand.getTag().getCompound("ElementalCircuit").get("Assembly");a.circuit(1).stop();h.assertTrue(CarrierRepository.commit(wand) && saved!=wand.getTag().getCompound("ElementalCircuit").get("Assembly"),"停供独立提交");
        h.assertTrue(JsonCodecs.assembly(CarrierRepository.load(wand.copy())).equals(JsonCodecs.assembly(a)),"复制后的物品拥有同一完整状态与独立实例");
        var graph=new WorldCircuitLinks();graph.setDirty(false);graph.deliver(k->null);graph.publish(k->null);h.assertTrue(!graph.isDirty(),"空世界网络不标记SavedData变更");h.succeed();
    }
    @GameTest(template="empty") public static void fixedRuntimeWithoutOnlineOwner(GameTestHelper h){
        var p=player(h);var b=fixed(h,new BlockPos(2,1,2),p);p.moveTo(Vec3.atCenterOf(b.getBlockPos()));b.charge(p);b.assembly().replace(1,AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.OUTPUT));b.assembly().circuit(1).start();
        long before=b.available();WorldCircuits.tick(h.getLevel(),id->null);
        h.assertTrue(b.assembly().circuit(1).snapshot().clock()==1 && b.available()==before-4*Fixed.ONE,"加载固定法阵由本地供给推进，与在线玩家解析无关");
        var saved=b.saveWithFullMetadata();var clone=new CircuitBlockEntity(b.getBlockPos(),b.getBlockState());clone.load(saved);h.assertTrue(clone.available()==b.available() && clone.assembly().circuit(1).snapshot().clock()==1,"保存边界提交离线运行现场");h.succeed();
    }
    @GameTest(template="empty") public static void destroyedEndpointRecoveryConservesMana(GameTestHelper h){
        var p=player(h);var from=fixed(h,new BlockPos(1,1,1),p);var to=fixed(h,new BlockPos(5,1,1),p);p.moveTo(Vec3.atCenterOf(from.getBlockPos()));from.charge(p);
        from.assembly().replace(1,AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.MANA_SEND));to.assembly().replace(1,AssemblyContracts.line(true,Route.Payload.MANA,Rune.Kind.MANA_RECEIVE,Rune.Kind.OUTPUT));
        var fa=new CircuitAddress(from.assembly().identity(),1,2);var ta=new CircuitAddress(to.assembly().identity(),1,1);var world=WorldCircuitLinks.get(h.getLevel());world.connect(h.getLevel(),p,fa.carrier(),new JsonCodecs.LinkRequest(fa,ta,Route.Payload.MANA));
        from.assembly().circuit(1).start();to.assembly().circuit(1).start();WorldCircuits.tick(h.getLevel());WorldCircuits.tick(h.getLevel());from.assembly().stopAll();to.assembly().stopAll();var link=world.graph().connections().stream().filter(c->c.from().equals(fa)).findFirst().orElseThrow();h.assertTrue(link.transit()!=null,"恢复测试拥有真实在途魔力");
        WorldCircuits.unregister(from);rejects(h,()->world.disconnect(h.getLevel(),p,ta.carrier(),link.id()),"仅卸载不能清理或吞掉运输");WorldCircuits.register(from);
        CarrierRepository.commit(from.carrier());var recovered=from.carrier().copy();h.getLevel().destroyBlock(from.getBlockPos(),false);var relocated=fixed(h,new BlockPos(7,1,1),p);relocated.placed(recovered,p);var survivor=JsonCodecs.assembly(to.assembly());var before=world.graph().snapshot();
        var stranger=player(h);rejects(h,()->world.disconnect(h.getLevel(),stranger,ta.carrier(),link.id()),"其他玩家不能清理失效线路");
        var restored=WorldCircuitLinks.load(binaryUnchecked(world.save(new CompoundTag())));restored.disconnect(h.getLevel(),p,ta.carrier(),link.id());var after=restored.graph().snapshot();
        h.assertTrue(!restored.references(ta.carrier(),0) && after.accepted()==before.accepted() && after.loss()==before.loss()+link.transit().mana().total() && after.accepted()==after.loss()+after.delivered()+after.connections().stream().filter(c->c.transit()!=null).mapToLong(c->c.transit().mana().total()).sum(),"删除后的在途全部入损耗，恢复账本守恒");
        h.assertTrue(JsonCodecs.assembly(to.assembly()).equals(survivor),"清理不向剩余法阵退款或补能");world.disconnect(h.getLevel(),p,ta.carrier(),link.id());h.succeed();
    }
    private static CompoundTag binaryUnchecked(CompoundTag tag){try{return binary(tag);}catch(IOException error){throw new IllegalStateException(error);}}
    @GameTest(template="empty") public static void absentOwnerDoesNotGainAnonymousPermissions(GameTestHelper h){
        var p=player(h);UUID owner=UUID.randomUUID();var zombie=h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE,new BlockPos(3,1,3));
        h.assertTrue(CastPermissions.mayAffect(h.getLevel(),owner,zombie),"离线固定设备可作用于生物");
        h.assertTrue(!CastPermissions.mayAffect(h.getLevel(),owner,p) && !CastPermissions.mayAffect(h.getLevel(),p.getUUID(),p),"缺失所有者不能绕过玩家权限，也不能命中自己");
        h.assertTrue(!CastPermissions.mayChangeBlock(h.getLevel(),owner,h.absolutePos(new BlockPos(2,1,2))),"缺失所有者不能作为匿名施法者改方块");h.succeed();
    }
    @GameTest(template="empty") public static void idleWorldProfile(GameTestHelper h){
        var p=player(h);for(int count:new int[]{1,4,16,36}){
            List<ItemStack> items=new ArrayList<>();for(int i=0;i<count;i++){var w=new ItemStack(ElementalCircuits.WAND.get());var a=CarrierRepository.load(w);var b=dense(false);a.replace(1,b);for(int k=0;k<3;k++){int slot=a.add(b.rules());a.replace(slot,b);}CarrierRepository.commit(w);p.getInventory().setItem(i,w);items.add(w);}
            var original=items.stream().map(w->w.getTag().getCompound("ElementalCircuit").get("Assembly")).toList();double[] times=new double[40];
            for(int sample=-10;sample<40;sample++){long start=System.nanoTime();for(var w:items)WorldCircuits.enqueue(w,p);WorldCircuits.tick(h.getLevel(),k->null);if(sample>=0)times[sample]=(System.nanoTime()-start)/1e6;}
            for(int i=0;i<count;i++)h.assertTrue(items.get(i).getTag().getCompound("ElementalCircuit").get("Assembly")==original.get(i),"性能样本待机不写NBT");Arrays.sort(times);
            org.slf4j.LoggerFactory.getLogger("RuntimeBoundary").info(String.format(Locale.ROOT,"REFACTOR WORLD_IDLE carriers=%d median_ms=%.3f p95_ms=%.3f",count,times[20],times[38]));
        }h.succeed();
    }
}
