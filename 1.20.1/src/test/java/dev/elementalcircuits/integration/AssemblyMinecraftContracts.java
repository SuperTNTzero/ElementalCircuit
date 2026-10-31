package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.block.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.server.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class AssemblyMinecraftContracts {
    private static ServerPlayer player(GameTestHelper h){return net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"assembly-test"));}
    private static CircuitBlockEntity block(GameTestHelper h,BlockPos relative,ServerPlayer player){
        h.setBlock(relative,ElementalCircuits.CIRCUIT_BLOCK.get().defaultBlockState().setValue(CircuitBlock.FACING,Direction.SOUTH));
        var b=(CircuitBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(relative));b.placed(new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get()),player);WorldCircuits.register(b);return b;
    }
    private static void request(ServerPlayer p,ItemStack wand,int slot,int action,String payload){CarrierService.forget(p.getUUID());CarrierService.request(p,CarrierService.identity(wand),slot,action,payload);}
    @GameTest(template="empty")
    public static void directMaterialChoiceAndStableSlots(GameTestHelper h){
        var p=player(h);var wand=new ItemStack(ElementalCircuits.WAND.get());p.getInventory().setItem(0,wand);p.getInventory().selected=0;
        p.getInventory().setItem(1,new ItemStack(ElementalCircuits.CRYSTAL_PLATE.get(),2));var id=CarrierService.identity(wand);
        request(p,wand,1,4,"CRYSTAL");var a=CarrierService.assembly(wand);
        h.assertTrue(a.circuit(1).definition().rules().material()==Rules.Material.CRYSTAL,"没有黄铜也能直接安装水晶基板");
        h.assertTrue(p.getInventory().countItem(ElementalCircuits.CRYSTAL_PLATE.get())==1 && p.getInventory().countItem(ElementalCircuits.WOOD_PLATE.get())==1,"安装消费目标板并回收旧板");
        request(p,wand,1,9,"CRYSTAL");h.assertTrue(a.slots().equals(List.of(1,2)) && a.selected()==2 && a.circuit(2).held()==0,"新增实际消费基板且没有额外魔力");
        request(p,wand,2,8,"1");request(p,wand,2,0,JsonCodecs.blueprint(AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.OUTPUT).withMaterial(Rules.Material.CRYSTAL)));
        h.assertTrue(a.selected()==1 && a.circuit(1).definition().runes().isEmpty() && a.circuit(2).definition().runes().size()==2,"迟到的明确槽位请求不会误刻当前阵");
        var before=JsonCodecs.assembly(a);request(p,wand,1,4,"BRASS");h.assertTrue(JsonCodecs.assembly(a).equals(before),"缺板安装拒绝且全部法阵原子不变");
        ItemStack restored=ItemStack.of(wand.save(new CompoundTag()));h.assertTrue(CarrierService.identity(restored).equals(id) && JsonCodecs.assembly(CarrierService.assembly(restored)).equals(before),"多阵物品完整保存");h.succeed();
    }
    @GameTest(template="empty")
    public static void legacyCarrierMigrationAndSharedSupply(GameTestHelper h){
        var p=player(h);var wand=new ItemStack(ElementalCircuits.WAND.get());p.getInventory().setItem(0,wand);var circuit=new TickCircuit(AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.OUTPUT));circuit.start();circuit.tick(PlayerMana.get(p));
        var tag=wand.getOrCreateTagElement("ElementalCircuit");UUID id=UUID.randomUUID();tag.putUUID("Identity",id);String old=JsonCodecs.snapshot(circuit).replace("\"version\":4","\"version\":1");tag.putString("Runtime",old);
        var a=CarrierService.assembly(wand);h.assertTrue(a.slots().equals(List.of(1)) && a.links().connections().isEmpty() && a.circuit(1).held()==circuit.held() && a.circuit(1).powered(),"旧法杖迁移为单槽且保留完整现场");
        a.circuit(1).stop();a.replace(1,AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.MANA_SEND));a.add(RuleResources.defaults());a.replace(2,AssemblyContracts.line(true,Route.Payload.MANA,Rune.Kind.MANA_RECEIVE,Rune.Kind.OUTPUT));
        a.links().connect(new CircuitAddress(id,1,2),new CircuitAddress(id,2,1),Route.Payload.MANA,k->a);a.slots().forEach(s->a.circuit(s).start());long spent=PlayerMana.get(p).snapshot().spent();
        for(int i=0;i<10;i++){WorldCircuits.enqueue(wand,p);WorldCircuits.tick(h.getLevel(),k->null);}
        h.assertTrue(PlayerMana.get(p).snapshot().spent()>spent && a.circuit(2).snapshot().delivered()>0 && a.circuit(2).snapshot().supplied()>0,"真实世界调度使用玩家供给并跨阵传输");
        h.assertTrue(!tag.contains("Runtime") && tag.contains("Assembly"),"成功验证后才替换旧记录");h.succeed();
    }
    @GameTest(template="empty")
    public static void fixedChargePersistenceAndRecovery(GameTestHelper h){
        var p=player(h);var b=block(h,new BlockPos(2,1,2),p);p.moveTo(Vec3.atCenterOf(b.getBlockPos()));
        h.assertTrue(h.getLevel().getRecipeManager().byKey(new ResourceLocation(ElementalCircuits.ID,"fixed_circuit")).isPresent(),"固定法阵有生存配方");
        h.assertTrue(b.available()==0 && b.carrier().getMaxStackSize()==1,"新方块零储量且不可堆叠");long before=PlayerMana.get(p).available();b.charge(p);
        h.assertTrue(b.available()==16*Fixed.ONE && PlayerMana.get(p).available()==before-16*Fixed.ONE,"充能从玩家真实转入16魔力");
        var stranger=player(h);stranger.moveTo(p.position());boolean denied=false;try{b.charge(stranger);}catch(IllegalArgumentException e){denied=true;}h.assertTrue(denied && b.available()==16*Fixed.ONE,"其他玩家不能操作或充能");
        b.assembly().add(RuleResources.defaults().withMaterial(Rules.Material.BRASS));CarrierService.persist(b.carrier());var saved=b.saveWithFullMetadata();
        var clone=new CircuitBlockEntity(b.getBlockPos(),b.getBlockState());clone.load(saved);h.assertTrue(clone.saveWithFullMetadata().equals(saved),"方块完整NBT恢复含全部槽位与储量");
        var drops=Block.getDrops(b.getBlockState(),h.getLevel(),b.getBlockPos(),b,p,ItemStack.EMPTY);h.assertTrue(drops.size()==1 && drops.get(0).save(new CompoundTag()).equals(b.carrier().save(new CompoundTag())),"拆除只产生一件完整载体，无储量复制");
        var replacement=block(h,new BlockPos(3,1,2),p);replacement.placed(drops.get(0),p);h.assertTrue(replacement.available()==16*Fixed.ONE && replacement.assembly().slots().size()==2,"重新放置保留储量与槽位");
        var badLedger=ItemStack.of(b.carrier().save(new CompoundTag()));badLedger.getOrCreateTagElement("FixedCircuit").putLong("Received",0);var badBlock=new CircuitBlockEntity(b.getBlockPos(),b.getBlockState());var badTag=saved.copy();badTag.put("Carrier",badLedger.save(new CompoundTag()));badBlock.load(badTag);boolean rejected=false;try{badBlock.available();}catch(IllegalArgumentException e){rejected=true;}h.assertTrue(rejected && badBlock.saveWithFullMetadata().equals(badTag),"伪造固定储量拒绝并保留原始数据");
        var malformed=saved.copy();malformed.putString("Carrier","原始损坏记录");badBlock.load(malformed);boolean disabled=false;try{badBlock.carrier();}catch(IllegalArgumentException e){disabled=true;}h.assertTrue(disabled && badBlock.saveWithFullMetadata().equals(malformed),"异常载体保留完整原始NBT，不让区块加载静默删除方块实体");
        h.assertTrue(b.getBlockState().getPistonPushReaction()==net.minecraft.world.level.material.PushReaction.BLOCK,"活塞不能移动锚点");h.succeed();
    }
    @GameTest(template="empty")
    public static void fixedLinksCausalityUnloadedAndBreakGuard(GameTestHelper h){
        var p=player(h);var b=block(h,new BlockPos(1,1,1),p);var target=block(h,new BlockPos(5,1,1),p);p.moveTo(Vec3.atCenterOf(b.getBlockPos()));b.charge(p);
        b.assembly().replace(1,AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.MANA_SEND));target.assembly().replace(1,AssemblyContracts.line(true,Route.Payload.MANA,Rune.Kind.MANA_RECEIVE,Rune.Kind.OUTPUT));
        var from=new CircuitAddress(b.assembly().identity(),1,2);var to=new CircuitAddress(target.assembly().identity(),1,1);var graph=WorldCircuitLinks.get(h.getLevel());graph.connect(h.getLevel(),p,from.carrier(),new JsonCodecs.LinkRequest(from,to,Route.Payload.MANA));
        var event=new net.minecraftforge.event.level.BlockEvent.BreakEvent(h.getLevel(),b.getBlockPos(),b.getBlockState(),p);ServerEvents.breaking(event);h.assertTrue(event.isCanceled(),"外部连线未拆除时拒绝破坏锚点");
        b.assembly().circuit(1).start();target.assembly().circuit(1).start();WorldCircuits.tick(h.getLevel(),id->p);h.assertTrue(target.assembly().circuit(1).snapshot().supplied()==0,"第一世界步不能跨阵瞬时传输");
        WorldCircuits.tick(h.getLevel(),id->p);var inFlight=graph.save(new CompoundTag());h.assertTrue(graph.graph().connections().stream().anyMatch(c->c.from().equals(from) && c.transit()!=null),"第二步源输出进入在途");
        var restored=WorldCircuitLinks.load(inFlight);h.assertTrue(restored.save(new CompoundTag()).equals(inFlight),"跨方块线路和锚点完整恢复");
        WorldCircuits.unregister(target);WorldCircuits.tick(h.getLevel(),id->p);h.assertTrue(target.assembly().circuit(1).snapshot().supplied()==0 && graph.graph().connections().stream().anyMatch(c->c.from().equals(from) && c.transit()!=null),"未加载接收块不收魔力也不丢包");
        WorldCircuits.register(target);for(int i=0;i<4;i++)WorldCircuits.tick(h.getLevel(),id->p);
        h.assertTrue(target.assembly().circuit(1).snapshot().delivered()>0 && b.available()==12*Fixed.ONE,"固定阵跨块施法且只扣源本地储量");
        b.assembly().stopAll();target.assembly().stopAll();int link=graph.graph().connections().stream().filter(c->c.from().equals(from)).findFirst().orElseThrow().id();graph.disconnect(h.getLevel(),p,from.carrier(),link);
        var clear=new net.minecraftforge.event.level.BlockEvent.BreakEvent(h.getLevel(),b.getBlockPos(),b.getBlockState(),p);ServerEvents.breaking(clear);h.assertTrue(!clear.isCanceled(),"停止并排空拆线后可以回收方块");h.succeed();
    }
    @GameTest(template="empty")
    public static void fixedDirectionActuallyCasts(GameTestHelper h){
        var p=player(h);var b=block(h,new BlockPos(3,1,2),p);p.moveTo(Vec3.atCenterOf(b.getBlockPos()));b.charge(p);
        var board=AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.CONVERT,Rune.Kind.DIRECTED,Rune.Kind.BEAM,Rune.Kind.OUTPUT);
        b.assembly().replace(1,board);var zombie=h.spawn(EntityType.ZOMBIE,new BlockPos(3,1,5));zombie.setNoAi(true);float health=zombie.getHealth();b.assembly().circuit(1).start();
        for(int i=0;i<8;i++)WorldCircuits.tick(h.getLevel(),id->p);
        h.assertTrue(zombie.getHealth()<health,"固定阵从方块正面发射真实服务器法术");h.assertTrue(b.available()==12*Fixed.ONE,"射击消耗本地魔力");h.succeed();
    }
}
