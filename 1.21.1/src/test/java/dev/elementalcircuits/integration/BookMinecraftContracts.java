package dev.elementalcircuits.integration;
import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.server.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class BookMinecraftContracts {
    private static ServerPlayer player(GameTestHelper h){var p=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"book-test"));p.getInventory().selected=0;return p;}
    private static ItemStack wand(ServerPlayer p){var s=new ItemStack(ElementalCircuits.WAND.get());p.getInventory().setItem(0,s);return s;}
    private static ItemStack book(ServerPlayer p){var s=new ItemStack(ElementalCircuits.SPELL_BOOK.get());p.setItemSlot(EquipmentSlot.OFFHAND,s);return s;}
    private static Blueprint line(){return AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.OUTPUT);}
    private static void edit(ServerPlayer p,ItemStack s,int slot,int action,String text){var table=p.blockPosition().above(2);p.serverLevel().setBlock(table,ElementalCircuits.TABLE.get().defaultBlockState(),3);InscriptionAccess.interact(p,table);CarrierService.forget(p.getUUID());CarrierService.request(p,CarrierService.identity(s),slot,action,text);}
    private static void spell(ServerPlayer p,ItemStack s,int action,int value){BookService.forget(p.getUUID());BookService.spellRequest(p,CarrierService.identity(s),action,value);}
    private static void notebook(ServerPlayer p,ItemStack s,int action,int page,String title,String code){BookService.forget(p.getUUID());BookService.notebookRequest(p,BookService.volume(s).identity(),action,page,title,code);}
    private static BlockPos work(GameTestHelper h,ServerPlayer p){var pos=h.absolutePos(new BlockPos(2,1,2));h.getLevel().setBlock(pos,ElementalCircuits.WAND_WORKBENCH.get().defaultBlockState(),3);ElementalCircuits.WAND_WORKBENCH.get().setPlacedBy(h.getLevel(),pos,h.getLevel().getBlockState(pos),p,new ItemStack(ElementalCircuits.WORKBENCH_ITEM.get()));p.moveTo(Vec3.atCenterOf(pos));WorkshopService.open(p,pos);return pos;}
    private static void install(ServerPlayer p,ItemStack s,BlockPos pos,String part){WorkshopService.forget(p.getUUID());WorkshopService.open(p,pos);WorkshopService.request(p,CarrierService.identity(s),pos.asLong(),0,part);}

    @GameTest(template="empty") public static void bookRegistrationsAndBlankIdentity(GameTestHelper h){
        for(String id:List.of("spellbook","blueprint_notebook","wand_workbench"))h.assertTrue(h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(ElementalCircuits.ID,id)).isPresent(),"新增物品有生存配方："+id);
        var p=player(h);var w=wand(p);var b=book(p);var n=new ItemStack(ElementalCircuits.NOTEBOOK.get());long mana=PlayerMana.get(p).available();
        var a=CarrierService.assembly(b);h.assertTrue(b.getMaxStackSize()==1 && n.getMaxStackSize()==1 && a.slots().equals(List.of(1)) && a.circuit(1).definition().runes().isEmpty() && a.circuit(1).held()==0,"两类书不可堆叠；新书一块空白木基板、零资源");
        h.assertTrue(!a.identity().equals(CarrierService.identity(w)) && BookService.volume(n).pages().isEmpty() && PlayerMana.get(p).available()==mana,"书身份独立且创建不补充玩家魔力");h.succeed();
    }
    @GameTest(template="empty") public static void bookEditsMaterialAndIndependentWand(GameTestHelper h){
        var p=player(h);var w=wand(p);var b=book(p);String wandBefore=JsonCodecs.assembly(CarrierService.assembly(w));p.getInventory().setItem(1,new ItemStack(ElementalCircuits.BRASS_PLATE.get(),2));
        edit(p,b,1,0,JsonCodecs.blueprint(line()));edit(p,b,1,9,"BRASS");var a=CarrierService.assembly(b);
        h.assertTrue(a.slots().equals(List.of(1,2)) && a.circuit(1).definition().equals(line()) && a.circuit(2).definition().rules().material()==Rules.Material.BRASS,"副手法术书直接保存自己的法阵和基板");
        h.assertTrue(p.getInventory().countItem(ElementalCircuits.BRASS_PLATE.get())==1 && wandBefore.equals(JsonCodecs.assembly(CarrierService.assembly(w))),"新增本书槽位消费实际基板，不修改法杖法阵");
        edit(p,b,1,4,"BRASS");h.assertTrue(a.circuit(1).definition().rules().material()==Rules.Material.BRASS && p.getInventory().countItem(ElementalCircuits.WOOD_PLATE.get())==1,"书中法阵也能实际更换基板并回收旧板");
        var copy=ItemStack.parseOptional(h.getLevel().registryAccess(),(CompoundTag)b.save(h.getLevel().registryAccess(),new CompoundTag()));h.assertTrue(JsonCodecs.assembly(CarrierService.assembly(copy)).equals(JsonCodecs.assembly(a)),"整本法术书完整NBT往返");h.succeed();
    }
    @GameTest(template="empty") public static void offhandBookCastsThroughWandAndSurvivesSwap(GameTestHelper h){
        var p=player(h);var w=wand(p);var b=book(p);var a=CarrierService.assembly(b);
        var draft=AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.CONVERT,Rune.Kind.AIM,Rune.Kind.DIRECTED,Rune.Kind.BEAM,Rune.Kind.OUTPUT);
        var runes=draft.runes().stream().map(r->r.kind()==Rune.Kind.AIM?new Rune(r.id(),r.kind(),r.cell(),r.element(),0,r.on(),r.signalControl(),r.weights()):r).toList();
        a.replace(1,new Blueprint(1,draft.rules(),runes,draft.routes(),false)); // Pin precision: this test verifies binding and wear, not random scatter.
        p.moveTo(h.absoluteVec(new Vec3(3.5,1,1.5)));p.setYRot(0);p.setXRot(0);var target=h.spawn(EntityType.ZOMBIE,new BlockPos(3,1,5));target.setNoAi(true);float hp=target.getHealth();long mana=PlayerMana.get(p).available();
        CarrierService.start(p,BookService.castCarrier(p,w));for(int i=0;i<10;i++){w.getItem().inventoryTick(w,h.getLevel(),p,0,true);b.getItem().inventoryTick(b,h.getLevel(),p,40,false);WorldCircuits.tick(h.getLevel(),k->null);}
        h.assertTrue(w.getDamageValue()>0 && b.getDamageValue()==0,"书中法术磨损由法杖承担");h.assertTrue(target.getHealth()<hp,"书中真实光束命中：health="+target.getHealth()+", before="+hp+", clock="+a.circuit(1).snapshot().clock()+", identity="+a.identity());h.assertTrue(PlayerMana.get(p).available()==mana-4*Fixed.ONE && CarrierService.get(w).snapshot().supplied()==0,"使用玩家唯一魔力槽，主手空阵不额外供给");
        var replacement=new ItemStack(ElementalCircuits.WAND.get());p.getInventory().setItem(0,replacement);h.assertTrue(BookService.castCarrier(p,replacement)==b && CarrierService.get(b).definition().runes().size()==6,"换杖后仍使用同一本书保存的法阵");h.succeed();
    }
    @GameTest(template="empty") public static void switchingPreservesLogicAndRejectsSpoof(GameTestHelper h){
        var p=player(h);wand(p);var b=book(p);var a=CarrierService.assembly(b);a.replace(1,LogicExamples.alternating(RuleResources.defaults(),LogicResources.defaults()));a.add(RuleResources.defaults());a.select(1);a.circuit(1).start();for(int i=0;i<17;i++)a.tick(PlayerMana.get(p),1);String before=JsonCodecs.snapshot(a.circuit(1));
        spell(p,b,0,1);h.assertTrue(a.selected()==2 && before.equals(JsonCodecs.snapshot(a.circuit(1))),"副手快速翻页保存当前计数、状态和账本");spell(p,b,0,1);h.assertTrue(a.selected()==1,"两页循环切回");spell(p,b,0,0);h.assertTrue(a.selected()==1,"非法切换方向拒绝");BookService.forget(p.getUUID());BookService.spellRequest(p,UUID.randomUUID(),0,1);h.assertTrue(a.selected()==1,"不能通过伪造书身份切换");
        p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);BookService.forget(p.getUUID());BookService.spellRequest(p,a.identity(),0,1);h.assertTrue(a.selected()==1,"未持有原书的迟到请求拒绝");h.succeed();
    }
    @GameTest(template="empty") public static void unequippedAndBrokenBookStopWithoutLoss(GameTestHelper h){
        var p=player(h);var w=wand(p);var b=book(p);var a=CarrierService.assembly(b);a.replace(1,line());a.circuit(1).start();a.tick(PlayerMana.get(p),1);long held=a.circuit(1).held(),spent=PlayerMana.get(p).snapshot().spent();
        WorldCircuits.enqueue(b,p);p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);WorldCircuits.tick(h.getLevel(),k->null);h.assertTrue(!a.powered() && a.circuit(1).held()==held && PlayerMana.get(p).snapshot().spent()==spent,"换手后排队任务拒绝供给和施法并保留残余");
        var restored=ItemStack.parseOptional(h.getLevel().registryAccess(),(CompoundTag)b.save(h.getLevel().registryAccess(),new CompoundTag()));p.setItemSlot(EquipmentSlot.OFFHAND,restored);w.setDamageValue(w.getMaxDamage());CarrierService.start(p,restored);h.assertTrue(!CarrierService.assembly(restored).powered(),"损坏法杖不能启动书法阵");CarrierService.assembly(restored).circuit(1).start();WorldCircuits.enqueue(restored,p);WorldCircuits.tick(h.getLevel(),k->null);h.assertTrue(!CarrierService.assembly(restored).powered() && CarrierService.get(restored).held()==held,"运行途中法杖损坏也停供并保留书状态");h.succeed();
    }
    @GameTest(template="empty") public static void physicalNotebookAtomicPages(GameTestHelper h){
        var p=player(h);var n=new ItemStack(ElementalCircuits.NOTEBOOK.get());p.getInventory().setItem(0,n);var id=BookService.volume(n).identity();String code=BlueprintTextCode.encode(line());
        notebook(p,n,1,0,"第一阵",code);notebook(p,n,1,0,"第二阵",code);notebook(p,n,0,1,"","");notebook(p,n,2,1,"已改名","");h.assertTrue(BookService.volume(n).selected()==1 && BookService.volume(n).page(1).title().equals("已改名"),"实体蓝图簿能新增、选择和改名");
        var before=n.save(h.getLevel().registryAccess(),new CompoundTag());notebook(p,n,1,0,"坏码","EC1.invalid");h.assertTrue(n.save(h.getLevel().registryAccess(),new CompoundTag()).equals(before),"非法文本码拒绝且原始书数据不变");
        var copy=ItemStack.parseOptional(h.getLevel().registryAccess(),(CompoundTag)n.save(h.getLevel().registryAccess(),new CompoundTag()));h.assertTrue(BookService.volume(copy).equals(BookService.volume(n)),"蓝图簿物品落地存读完整");notebook(p,n,3,1,"","");notebook(p,n,1,0,"第三阵",code);h.assertTrue(BookService.volume(n).pages().stream().map(BlueprintVolume.Page::id).toList().equals(List.of(2,3)),"删页不复用身份");
        p.getInventory().setItem(0,ItemStack.EMPTY);BookService.forget(p.getUUID());BookService.notebookRequest(p,id,3,2,"","");h.assertTrue(BookService.volume(n).pages().size()==2,"不在玩家库存中的簿子不接受请求");h.succeed();
    }
    @GameTest(template="empty") public static void workbenchInstallsAndRepairsWithInventory(GameTestHelper h){
        var p=player(h);var w=wand(p);var defs=WandResources.definitions();var next=defs.in(WandParts.Slot.HEAD).get(1);p.getInventory().setItem(1,new ItemStack(ElementalCircuits.PARTS.get(next.id()).get()));p.getInventory().setItem(2,new ItemStack(ElementalCircuits.WOOD_PLATE.get(),2));w.setDamageValue(63);var original=WandEquipment.assembly(w);String runtime=JsonCodecs.assembly(CarrierService.assembly(w));var pos=work(h,p);
        install(p,w,pos,next.id());h.assertTrue(WandEquipment.assembly(w).head().equals(next.id()) && w.getDamageValue()==63,"工作台更换杖端且保留磨损");h.assertTrue(p.getInventory().countItem(ElementalCircuits.PARTS.get(next.id()).get())==0 && p.getInventory().countItem(ElementalCircuits.PARTS.get(original.head()).get())==1 && JsonCodecs.assembly(CarrierService.assembly(w)).equals(runtime),"换件消费库存回收旧件，法阵不变");
        WorkshopService.forget(p.getUUID());WorkshopService.open(p,pos);WorkshopService.request(p,CarrierService.identity(w),pos.asLong(),1,"");h.assertTrue(w.getDamageValue()==15 && p.getInventory().countItem(ElementalCircuits.WOOD_PLATE.get())==1,"工作台修缮消费木基板并恢复48耐久");
        edit(p,w,1,7,"");h.assertTrue(w.getDamageValue()==15,"旧法阵编辑器修缮入口已拒绝");h.succeed();
    }
    @GameTest(template="empty") public static void workbenchSessionDistanceAndActiveBookGuard(GameTestHelper h){
        var p=player(h);var w=wand(p);var b=book(p);var part=WandResources.definitions().in(WandParts.Slot.TAIL).get(1);p.getInventory().setItem(1,new ItemStack(ElementalCircuits.PARTS.get(part.id()).get(),3));var pos=work(h,p);var base=WandEquipment.assembly(w);var a=CarrierService.assembly(b);a.replace(1,line());a.circuit(1).start();
        WorkshopService.request(p,CarrierService.identity(w),pos.asLong(),0,part.id());h.assertTrue(WandEquipment.assembly(w).equals(base) && p.getInventory().countItem(ElementalCircuits.PARTS.get(part.id()).get())==3,"副手书起阵时禁止换件");a.stopAll();WorkshopService.forget(p.getUUID());WorkshopService.open(p,pos);p.moveTo(Vec3.atCenterOf(pos).add(9,0,0));WorkshopService.request(p,CarrierService.identity(w),pos.asLong(),0,part.id());h.assertTrue(WandEquipment.assembly(w).equals(base),"离开工作台拒绝远程安装");
        p.moveTo(Vec3.atCenterOf(pos));WorkshopService.open(p,pos);h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),3);WorkshopService.request(p,CarrierService.identity(w),pos.asLong(),0,part.id());h.assertTrue(WandEquipment.assembly(w).equals(base),"方块消失拒绝旧会话请求");h.succeed();
    }
    @GameTest(template="empty") public static void corruptBooksKeepOriginalData(GameTestHelper h){
        var p=player(h);var b=book(p);dev.elementalcircuits.adapter.persistence.ItemData.update(b,t->{var c=t.getCompound("ElementalCircuit");c.putString("Assembly","损坏现场");t.put("ElementalCircuit",c);});var original=dev.elementalcircuits.adapter.persistence.ItemData.read(b).getCompound("ElementalCircuit").getString("Assembly");b.getItem().inventoryTick(b,h.getLevel(),p,40,false);h.assertTrue(dev.elementalcircuits.adapter.persistence.ItemData.read(b).getCompound("ElementalCircuit").getString("Assembly").equals(original),"法术书损坏不重置");
        var n=new ItemStack(ElementalCircuits.NOTEBOOK.get());dev.elementalcircuits.adapter.persistence.ItemData.update(n,t->t.putString("BlueprintVolume","损坏蓝图簿"));p.getInventory().setItem(0,n);BookService.openNotebook(p,n);h.assertTrue(dev.elementalcircuits.adapter.persistence.ItemData.read(n).getString("BlueprintVolume").equals("损坏蓝图簿"),"蓝图簿损坏不以空簿覆盖");h.succeed();
    }
}
