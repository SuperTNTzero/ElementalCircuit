package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;

import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.block.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.io.*;
import java.util.*;
@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class StateAudit {
 static void log(String s){org.slf4j.LoggerFactory.getLogger("StateAudit").info("AUDIT "+s);}
 static ServerPlayer player(GameTestHelper h){return net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"audit"));}
 static Blueprint dense(){List<Rune> runes=new ArrayList<>();for(int q=-6;q<=6;q++)for(int r=-6;r<=6;r++)if(new Hex(q,r).inside(6) && runes.size()<64)runes.add(Rune.create(runes.size()+1,Rune.Kind.SIGNAL,new Hex(q,r)));return new Blueprint(1,RuleResources.defaults().withMaterial(Rules.Material.BRASS),runes,List.of(),true);}
 static CompoundTag binary(CompoundTag t)throws Exception{var b=new ByteArrayOutputStream();NbtIo.writeCompressed(t,b);return NbtIo.readCompressed(new ByteArrayInputStream(b.toByteArray()));}
 @GameTest(template="empty") public static void binaryNotebookAndAssembly(GameTestHelper h)throws Exception{
  var note=new ItemStack(ElementalCircuits.NOTEBOOK.get());var v=BookService.volume(note);var b=dense();for(int i=0;i<16;i++)v=v.add("page "+i,b);BookService.write(note,v);String before=note.getTag().getString("BlueprintVolume");var restored=ItemStack.of(binary(note.save(new CompoundTag())));String after=restored.getTag().getString("BlueprintVolume");log("NOTEBOOK binary original_chars="+before.length()+" restored_chars="+after.length()+" equal="+before.equals(after));h.assertTrue(!before.equals(after),"审计：合法卷册实际二进制保存存在损失");
  var wand=new ItemStack(ElementalCircuits.WAND.get());var a=CarrierService.assembly(wand);a.replace(1,b);for(int i=0;i<3;i++){int id=a.add(b.rules());a.replace(id,b);}CarrierService.persist(wand);String raw=wand.getTag().getCompound("ElementalCircuit").getString("Assembly");var copy=ItemStack.of(binary(wand.save(new CompoundTag())));String read=copy.getTag().getCompound("ElementalCircuit").getString("Assembly");log("ASSEMBLY binary original_chars="+raw.length()+" restored_chars="+read.length()+" equal="+raw.equals(read));h.succeed();
 }
 static CircuitBlockEntity fixed(GameTestHelper h,BlockPos pos,ServerPlayer p){h.setBlock(pos,ElementalCircuits.CIRCUIT_BLOCK.get().defaultBlockState());var b=(CircuitBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos));b.placed(new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get()),p);WorldCircuits.register(b);return b;}
 @GameTest(template="empty") public static void missingEndpointCannotDisconnect(GameTestHelper h){
  var p=player(h);var from=fixed(h,new BlockPos(2,1,2),p);var to=fixed(h,new BlockPos(5,1,2),p);from.assembly().replace(1,AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.MANA_SEND));to.assembly().replace(1,AssemblyContracts.line(true,Route.Payload.MANA,Rune.Kind.MANA_RECEIVE,Rune.Kind.OUTPUT));var graph=WorldCircuitLinks.get(h.getLevel());graph.connect(h.getLevel(),p,from.assembly().identity(),new JsonCodecs.LinkRequest(new CircuitAddress(from.assembly().identity(),1,2),new CircuitAddress(to.assembly().identity(),1,1),Route.Payload.MANA));int id=graph.graph().connections().stream().filter(c->c.from().carrier().equals(from.assembly().identity())).findFirst().orElseThrow().id();h.getLevel().destroyBlock(from.getBlockPos(),false);boolean denied=false;String why="";try{graph.disconnect(h.getLevel(),p,to.assembly().identity(),id);}catch(RuntimeException e){denied=true;why=e.getMessage();}log("MISSING_ENDPOINT disconnect_denied="+denied+" live_endpoint_still_referenced="+graph.references(to.assembly().identity(),0)+" reason="+why);h.assertTrue(denied && graph.references(to.assembly().identity(),0),"审计：缺失端点保留悬挂线路，不能断线回收");h.succeed();
 }
 @GameTest(template="empty") public static void richAssemblyBinaryAndPacket(GameTestHelper h)throws Exception{
  var base=dense();List<Rune> nodes=base.runes().stream().map(r->Rune.create(r.id(),Rune.Kind.OR,r.cell())).toList();List<Route> wires=new ArrayList<>();for(var from:nodes)for(var to:nodes)if(wires.size()<64 && from.cell().distance(to.cell())==1)wires.add(new Route(from.id(),to.id(),Route.Payload.SIGNAL,List.of(from.cell(),to.cell())));var bp=new Blueprint(1,base.rules(),nodes,wires,true);var book=new ItemStack(ElementalCircuits.SPELL_BOOK.get());var a=CarrierService.assembly(book);a.replace(1,bp);for(int i=0;i<3;i++){int id=a.add(bp.rules());a.replace(id,bp);}CarrierService.persist(book);String before=book.getTag().getCompound("ElementalCircuit").getString("Assembly");var restored=ItemStack.of(binary(book.save(new CompoundTag())));String after=restored.getTag().getCompound("ElementalCircuit").getString("Assembly");log("RICH_ASSEMBLY nodes=64 routes=64 slots=4 original_chars="+before.length()+" restored_chars="+after.length());h.assertTrue(after.isEmpty(),"审计：允许的四槽有线法阵二进制保存变为空串");
  var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());buffer.writeItem(book);var client=buffer.readItem();log("ITEM_NETWORK assembly_chars="+client.getTag().getCompound("ElementalCircuit").getString("Assembly").length()+" wire_bytes="+buffer.writerIndex());buffer.release();
  var particle=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());new net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket(net.minecraft.core.particles.ParticleTypes.END_ROD,false,0,0,0,.06f,.06f,.06f,.01f,1).write(particle);log("PARTICLE packet_payload_bytes="+particle.writerIndex()+" shield_cap_broadcast_calls_per_second="+(64*12*20));particle.release();h.succeed();
 }
 @GameTest(template="empty") public static void idleWorldCosts(GameTestHelper h){
  var p=player(h);for(int count:new int[]{1,4,16,36}){List<ItemStack> items=new ArrayList<>();for(int i=0;i<count;i++){var w=new ItemStack(ElementalCircuits.WAND.get());var a=CarrierService.assembly(w);var b=dense();a.replace(1,b);for(int k=0;k<3;k++){int id=a.add(b.rules());a.replace(id,b);}items.add(w);}double[] times=new double[40];for(int n=-10;n<40;n++){long start=System.nanoTime();for(var w:items)WorldCircuits.enqueue(w,p);WorldCircuits.tick(h.getLevel(),k->null);if(n>=0)times[n]=(System.nanoTime()-start)/1e6;}Arrays.sort(times);log(String.format(Locale.ROOT,"WORLD_IDLE carriers=%d median_ms=%.3f p95_ms=%.3f assembly_chars=%d",count,times[20],times[38],items.get(0).getTag().getCompound("ElementalCircuit").getString("Assembly").length()));}h.succeed();
 }
}
