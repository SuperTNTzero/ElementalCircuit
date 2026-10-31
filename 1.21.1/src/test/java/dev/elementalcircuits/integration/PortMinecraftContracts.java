package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.block.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.server.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class PortMinecraftContracts {
    @GameTest(template="empty") public static void dataComponentIsolationAndInvalidation(GameTestHelper h){
        var wand=new ItemStack(ElementalCircuits.WAND.get());var original=CarrierRepository.load(wand);
        original.replace(1,AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.OUTPUT));CarrierRepository.commit(wand);
        var component=wand.get(DataComponents.CUSTOM_DATA);var detached=ItemData.read(wand);detached.putString("Uncommitted","草稿");
        h.assertTrue(!ItemData.read(wand).contains("Uncommitted") && wand.get(DataComponents.CUSTOM_DATA)==component,"读取副本不能偷偷改写物品组件");
        var other=wand.copy();ItemData.update(other,t->t.putString("CopyOnly","独立副本"));
        h.assertTrue(!ItemData.read(wand).contains("CopyOnly") && ItemData.read(other).contains("CopyOnly"),"物品复制共享不可变组件，写回后彼此隔离");
        ItemData.update(wand,t->{var c=t.getCompound("ElementalCircuit");c.putString("Assembly","损坏的替换组件");t.put("ElementalCircuit",c);});
        boolean rejected=false;try{CarrierRepository.load(wand);}catch(RuntimeException e){rejected=true;}
        h.assertTrue(rejected,"替换组件使运行缓存失效，不能继续使用旧的合法现场");h.succeed();
    }
    @GameTest(template="empty") public static void nestedLegacyCarrierUpgradesWithoutRefill(GameTestHelper h){
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"port-test"));
        var pos=h.absolutePos(new BlockPos(2,1,2));var state=ElementalCircuits.CIRCUIT_BLOCK.get().defaultBlockState().setValue(CircuitBlock.FACING,Direction.SOUTH);h.getLevel().setBlock(pos,state,3);
        var block=(CircuitBlockEntity)h.getLevel().getBlockEntity(pos);block.placed(new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get()),player);player.moveTo(Vec3.atCenterOf(pos));block.charge(player);
        var assembly=block.assembly();assembly.replace(1,AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.OUTPUT));assembly.circuit(1).start();assembly.tick(block,1);CarrierRepository.commit(block.carrier());
        var before=dev.elementalcircuits.adapter.JsonCodecs.assembly(assembly);long charge=block.available();
        var legacy=new CompoundTag();legacy.putString("id","elementalcircuits:fixed_circuit");legacy.putByte("Count",(byte)1);legacy.put("tag",ItemData.read(block.carrier()));
        var full=block.saveWithFullMetadata(h.getLevel().registryAccess());full.put("Carrier",legacy);
        var restored=new CircuitBlockEntity(pos,state);restored.loadWithComponents(full,h.getLevel().registryAccess());
        h.assertTrue(restored.owner().equals(player.getUUID()) && restored.available()==charge && dev.elementalcircuits.adapter.JsonCodecs.assembly(restored.assembly()).equals(before),"1.20私有嵌套物品经原版DFU升级，所有者、余额、身份和活动法阵完整保留");
        var upgraded=restored.saveWithFullMetadata(h.getLevel().registryAccess()).getCompound("Carrier");
        h.assertTrue(upgraded.contains("components",Tag.TAG_COMPOUND) && !upgraded.contains("tag") && !upgraded.contains("Count"),"成功恢复后用1.21组件格式保存");
        for(byte count:new byte[]{0,2}){var broken=full.copy();broken.getCompound("Carrier").putByte("Count",count);var denied=new CircuitBlockEntity(pos,state);denied.loadWithComponents(broken,h.getLevel().registryAccess());boolean rejected=false;try{denied.available();}catch(RuntimeException e){rejected=true;}h.assertTrue(rejected && denied.saveWithFullMetadata(h.getLevel().registryAccess()).equals(broken),"非法旧载体数量保留原文并停用，不能变为空白新阵");}
        h.succeed();
    }
    @GameTest(template="empty") public static void legacyWandRetainsDamageAndCircuit(GameTestHelper h){
        var wand=new ItemStack(ElementalCircuits.WAND.get());var identity=CarrierRepository.identity(wand);var assembly=CarrierRepository.load(wand);assembly.replace(1,AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.OUTPUT));CarrierRepository.commit(wand);
        var legacy=new CompoundTag();legacy.putString("id","elementalcircuits:circuit_wand");legacy.putByte("Count",(byte)1);var data=ItemData.read(wand);data.putInt("Damage",57);legacy.put("tag",data);
        var upgraded=NativeStackCodec.read(h.getLevel().registryAccess(),legacy);
        h.assertTrue(upgraded.getDamageValue()==57 && upgraded.getMaxDamage()==192 && CarrierRepository.identity(upgraded).equals(identity) && CarrierRepository.load(upgraded).circuit(1).definition().equals(assembly.circuit(1).definition()),"原版损伤与自定义法阵一起转换，不修复法杖或重置设计");h.succeed();
    }
}
