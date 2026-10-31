package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.server.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class CooldownMinecraftContracts {
    @GameTest(template="empty") public static void componentCodecRejectsInvalidCrossFields(GameTestHelper h){
        var codec=CooldownComponent.TYPE.get().codec();
        for(String text:java.util.List.of("{\"version\":1,\"remaining\":13,\"duration\":12}","{\"version\":2,\"remaining\":0,\"duration\":12}","{\"version\":1,\"remaining\":-1,\"duration\":12}"))
            h.assertTrue(codec.parse(com.mojang.serialization.JsonOps.INSTANCE,com.google.gson.JsonParser.parseString(text)).error().isPresent(),"非法冷却以编解码错误拒绝，不让构造异常逃出存档读取");
        h.assertTrue(codec.parse(com.mojang.serialization.JsonOps.INSTANCE,com.google.gson.JsonParser.parseString("{\"version\":1,\"remaining\":5,\"duration\":12}")).getOrThrow().equals(new CastCooldown(5,12)),"合法组件完整恢复");h.succeed();
    }
    @GameTest(template="empty") public static void cooldownSurvivesInstallationAndSerialization(GameTestHelper h){
        var p=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"cooldown-parts"));
        var w=new ItemStack(ElementalCircuits.WAND.get());p.getInventory().selected=0;p.getInventory().setItem(0,w);
        var runtime=CarrierRepository.load(w);String before=JsonCodecs.assembly(runtime);
        h.assertTrue(WandCooldown.tryRelease(w),"新法杖首次可释放");
        h.assertTrue(!WandCooldown.tryRelease(w),"同一步不能重复释放");
        p.getInventory().setItem(1,new ItemStack(ElementalCircuits.PARTS.get("copper_head").get()));WandEquipment.install(p,w,"copper_head");
        h.assertTrue(WandCooldown.state(w).remaining()==12,"换快杖端不清除当前冷却");
        h.assertTrue(CarrierRepository.load(w)==runtime && JsonCodecs.assembly(runtime).equals(before),"冷却组件和部件安装不重建运行实例");
        for(int i=0;i<7;i++)WandCooldown.tick(w);
        var loaded=ItemStack.parseOptional(h.getLevel().registryAccess(),(CompoundTag)w.save(h.getLevel().registryAccess(),new CompoundTag()));
        h.assertTrue(WandCooldown.state(loaded).remaining()==5,"物品完整编码保存剩余步数");
        for(int i=0;i<5;i++)WandCooldown.tick(loaded);
        h.assertTrue(WandCooldown.tryRelease(loaded) && WandCooldown.state(loaded).duration()==8,"下次释放按新部件速度计算");
        h.succeed();
    }
    @GameTest(template="empty") public static void bookUsesPhysicalWandCooldownAndVmKeepsOneStep(GameTestHelper h){
        var p=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"cooldown-book"));
        var w=new ItemStack(ElementalCircuits.WAND.get());p.getInventory().selected=0;p.getInventory().setItem(0,w);
        p.getInventory().setItem(1,new ItemStack(ElementalCircuits.PARTS.get("copper_head").get()));WandEquipment.install(p,w,"copper_head");
        var b=new ItemStack(ElementalCircuits.SPELL_BOOK.get());p.setItemSlot(EquipmentSlot.OFFHAND,b);
        var a=CarrierRepository.load(b);var board=dev.elementalcircuits.domain.AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.CONVERT,Rune.Kind.OUTPUT);a.replace(1,board);a.circuit(1).start();
        h.assertTrue(WandCooldown.tryRelease(w),"先占用法杖释放预算");
        for(int i=0;i<7;i++){WorldCircuits.enqueue(b,p);WorldCircuits.tick(h.getLevel(),id->null);}
        h.assertTrue(a.circuit(1).snapshot().clock()==7 && a.circuit(1).held()>0 && w.getDamageValue()==0,"快杖不加速VM，冷却时书输出等待且不磨损");
        var swapped=new ItemStack(ElementalCircuits.SPELL_BOOK.get());p.setItemSlot(EquipmentSlot.OFFHAND,swapped);
        h.assertTrue(!WandCooldown.tryRelease(CastingEquipment.wand(p,swapped)),"换书不会绕过物理法杖的CD");
        p.setItemSlot(EquipmentSlot.OFFHAND,b);for(int i=0;i<8;i++)WandCooldown.tick(w);
        WorldCircuits.enqueue(b,p);WorldCircuits.tick(h.getLevel(),id->null);
        h.assertTrue(w.getDamageValue()==1 && WandCooldown.state(w).remaining()==8 && a.circuit(1).held()==0,"就绪后原书交付一次并设置新的CD");
        h.assertTrue(!b.has(CooldownComponent.TYPE.get()),"法术书本身不复制法杖冷却");h.succeed();
    }
}
