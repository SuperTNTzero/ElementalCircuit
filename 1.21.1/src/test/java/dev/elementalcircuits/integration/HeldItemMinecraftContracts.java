package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.server.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class HeldItemMinecraftContracts {
    @GameTest(template="empty") public static void wandRuntimeChangesDoNotReequip(GameTestHelper h){
        var wand=new ItemStack(ElementalCircuits.WAND.get());var a=CarrierRepository.load(wand);var before=wand.copy();
        WandCooldown.tryRelease(wand);WandEquipment.wear(wand);a.replace(1,AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.OUTPUT));CarrierRepository.commit(wand);
        for(int i=0;i<20;i++){
            h.assertTrue(!wand.getItem().shouldCauseReequipAnimation(before,wand,false),"冷却、磨损和法阵数据更新不触发重新装备");WandCooldown.tick(wand);
        }
        h.assertTrue(wand.getItem().shouldCauseReequipAnimation(before,wand,true),"真实换槽保留装备动画");
        var another=new ItemStack(ElementalCircuits.WAND.get());CarrierRepository.load(another);
        h.assertTrue(wand.getItem().shouldCauseReequipAnimation(wand,another,false),"不同身份法杖保留装备动画");
        var changed=wand.copy();CompoundTag parts=new CompoundTag();parts.putInt("Version",1);parts.putString("Head","copper_head");parts.putString("Body",WandEquipment.assembly(wand).body());parts.putString("Tail",WandEquipment.assembly(wand).tail());parts.putLong("Remainder",0);ItemData.put(changed,"WandAssembly",parts);
        h.assertTrue(wand.getItem().shouldCauseReequipAnimation(wand,changed,false),"外形部件更换仍刷新装备");
        h.assertTrue(wand.getItem().shouldCauseReequipAnimation(wand,new ItemStack(Items.STICK),false),"其他物品不被法杖规则吞掉");
        String encoded=ItemData.read(wand).toString();for(int i=0;i<20;i++){ItemAppearance.identity(wand);ItemAppearance.parts(wand);}h.assertTrue(encoded.equals(ItemData.read(wand).toString()),"显示投影不创建身份或反写数据");h.succeed();
    }
    @GameTest(template="empty") public static void bookPageChangesDoNotReequip(GameTestHelper h){
        var book=new ItemStack(ElementalCircuits.SPELL_BOOK.get());var a=CarrierRepository.load(book);var before=book.copy();a.add(dev.elementalcircuits.adapter.RuleResources.defaults());CarrierRepository.commit(book);
        h.assertTrue(!book.getItem().shouldCauseReequipAnimation(before,book,false),"换阵数据不收回法术书");
        var notebook=new ItemStack(ElementalCircuits.NOTEBOOK.get());var volume=NotebookRepository.load(notebook);before=notebook.copy();NotebookRepository.save(notebook,volume.add("火焰",AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.OUTPUT)));
        h.assertTrue(!notebook.getItem().shouldCauseReequipAnimation(before,notebook,false),"收录蓝图不收回蓝图簿");
        h.assertTrue(ItemAppearance.notebookIdentity(notebook).equals(volume.identity()),"只读取簿身份，不解码全部蓝图");
        var another=new ItemStack(ElementalCircuits.NOTEBOOK.get());NotebookRepository.load(another);h.assertTrue(notebook.getItem().shouldCauseReequipAnimation(notebook,another,false),"不同蓝图簿仍重新装备");h.succeed();
    }
}
