package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.server.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class MinecraftContracts {
    private MinecraftContracts(){ }
    @GameTest(template="empty")
    public static void registrationAndItemPersistence(GameTestHelper helper){
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(new ResourceLocation(ElementalCircuits.ID,"circuit_wand")).isPresent(),"法杖必须有生存配方");
        ItemStack original=new ItemStack(ElementalCircuits.WAND.get());UUID id=CarrierService.identity(original);
        TickCircuit circuit=CarrierService.get(original);
        helper.assertTrue(circuit.definition().runes().isEmpty(),"新法杖必须是空白法阵");
        ItemStack loaded=ItemStack.of(original.save(new CompoundTag()));
        helper.assertTrue(CarrierService.identity(loaded).equals(id),"物品序列化保留身份");
        helper.assertTrue(JsonCodecs.snapshot(CarrierService.get(loaded)).equals(JsonCodecs.snapshot(circuit)),"物品恢复保留完整现场");
        helper.assertTrue(loaded.getMaxStackSize()==1,"法杖不能堆叠");helper.succeed();
    }
    @GameTest(template="empty")
    public static void beamObstructionAndDamage(GameTestHelper helper){
        var level=helper.getLevel();var zombie=helper.spawn(EntityType.ZOMBIE,new BlockPos(1,1,5));zombie.setNoAi(true);zombie.setInvulnerable(false);
        Vec3 origin=helper.absoluteVec(new Vec3(1.5,1.5,1.5));
        Mixture fire=Mixture.pure(4*Fixed.ONE).convert(Element.FIRE,Fixed.ONE);
        SpellSpec spec=new SpellSpec(SpellSpec.Method.DIRECTED,SpellSpec.Shape.BEAM,0,0,0,0);
        UUID carrier=new UUID(27,1);var cast=new MagicCircuit.Cast(0,fire,spec);
        helper.setBlock(new BlockPos(1,1,3),Blocks.STONE);
        float before=zombie.getHealth();SpellEffects.cast(level,null,origin,new Vec3(0,0,1),carrier,cast,RuleResources.defaults());
        helper.assertTrue(zombie.getHealth()==before,"光束不能穿过实体方块伤害墙后目标");
        helper.setBlock(new BlockPos(1,1,3),Blocks.AIR);
        SpellEffects.cast(level,null,origin,new Vec3(0,0,1),carrier,cast,RuleResources.defaults());
        helper.assertTrue(zombie.getHealth()<before,"无阻挡的光束必须实际造成服务器伤害");helper.succeed();
    }
    @GameTest(template="empty")
    public static void worldSpellPersistence(GameTestHelper helper){
        WorldSpells spells=new WorldSpells();UUID id=new UUID(27,2);
        spells.launch(id,helper.absoluteVec(new Vec3(2,2,2)),new Vec3(0,0,1),12,2,new Packet(Mixture.pure(Fixed.ONE).convert(Element.FIRE,Fixed.ONE),SpellSpec.EMPTY));
        spells.shield(id,helper.absoluteVec(new Vec3(3,2,3)),new Vec3(0,0,1),2,Fixed.ONE);
        CompoundTag snapshot=spells.save(new CompoundTag());
        WorldSpells restored=WorldSpells.load(snapshot);
        helper.assertTrue(restored.save(new CompoundTag()).equals(snapshot),"在途弹道及屏障必须完整往返");
        spells.tick(helper.getLevel());restored.tick(helper.getLevel());
        helper.assertTrue(restored.save(new CompoundTag()).equals(spells.save(new CompoundTag())),"恢复后一个服务器步的轨迹一致");
        WorldSpells overflow=new WorldSpells();
        overflow.launch(id,helper.absoluteVec(new Vec3(7,5,7)),new Vec3(0,0,-1),12,1,new Packet(Mixture.pure(2*Fixed.ONE).convert(Element.FIRE,Fixed.ONE),SpellSpec.EMPTY));
        overflow.shield(new UUID(27,3),helper.absoluteVec(new Vec3(7,5,6.75)),new Vec3(0,0,1),1,Fixed.ONE/8);
        overflow.tick(helper.getLevel());CompoundTag after=overflow.save(new CompoundTag());
        helper.assertTrue(after.getList("Shields",10).isEmpty(),"有限屏障耗尽后消失");
        helper.assertTrue(after.getList("Flights",10).size()==1 && JsonCodecs.readPacket(after.getList("Flights",10).getCompound(0).getString("Packet")).mana().total()==Fixed.ONE,"屏障只吸收容量内份额，溢出成分继续飞行");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void componentInventoryAndAttributes(GameTestHelper helper){
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),new com.mojang.authlib.GameProfile(new UUID(28,10),"wand-parts-test"));ItemStack wand=new ItemStack(ElementalCircuits.WAND.get());
        player.getInventory().setItem(0,wand);UUID id=CarrierService.identity(wand);String runtime=JsonCodecs.snapshot(CarrierService.get(wand));wand.setDamageValue(40);
        player.getInventory().setItem(1,new ItemStack(ElementalCircuits.PARTS.get("amethyst_head").get()));
        WandEquipment.install(player,wand,"amethyst_head");
        helper.assertTrue(player.getInventory().countItem(ElementalCircuits.PARTS.get("amethyst_head").get())==0 && player.getInventory().countItem(ElementalCircuits.PARTS.get("quartz_head").get())==1,"安装消费新部件并回收旧件");
        helper.assertTrue(WandEquipment.stats(wand).strength()==98304 && wand.getMaxDamage()==256,"杖端属性与动态总耐久实际生效");
        helper.assertTrue(wand.getDamageValue()==40 && CarrierService.identity(wand).equals(id) && JsonCodecs.snapshot(CarrierService.get(wand)).equals(runtime),"安装不重置磨损、身份和法阵现场");
        var before=wand.save(new CompoundTag());boolean rejected=false;try{WandEquipment.install(player,wand,"diamond_tail");}catch(IllegalArgumentException expected){rejected=true;}
        helper.assertTrue(rejected && wand.save(new CompoundTag()).equals(before),"缺部件拒绝且现场原子不变");
        player.getInventory().add(new ItemStack(ElementalCircuits.PARTS.get("diamond_tail").get()));WandEquipment.install(player,wand,"diamond_tail");
        double damage=wand.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND).get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).stream().mapToDouble(net.minecraft.world.entity.ai.attributes.AttributeModifier::getAmount).sum()+1;
        helper.assertTrue(damage==7,"杖尾通过原版近战属性接口提供实际伤害");
        wand.setDamageValue(wand.getMaxDamage());helper.assertTrue(WandEquipment.broken(wand) && !wand.isEmpty(),"耗尽保留载体而不销毁法阵");
        player.getInventory().add(new ItemStack(ElementalCircuits.WOOD_PLATE.get()));WandEquipment.repair(player,wand);
        helper.assertTrue(!WandEquipment.broken(wand) && wand.getDamageValue()==wand.getMaxDamage()-48,"修缮只恢复指定耐久");
        ItemStack loaded=ItemStack.of(wand.save(new CompoundTag()));helper.assertTrue(WandEquipment.assembly(loaded).equals(WandEquipment.assembly(wand)) && loaded.getDamageValue()==wand.getDamageValue(),"部件和磨损完整保存");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void cadenceRuntimeAndMigration(GameTestHelper helper){
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),new com.mojang.authlib.GameProfile(new UUID(28,11),"wand-cadence-test"));ItemStack wand=new ItemStack(ElementalCircuits.WAND.get());
        player.getInventory().setItem(0,wand);player.getInventory().setItem(1,new ItemStack(ElementalCircuits.PARTS.get("copper_head").get()));WandEquipment.install(player,wand,"copper_head");
        int count=0;for(int i=0;i<7;i++)count+=WandEquipment.steps(wand);ItemStack loaded=ItemStack.of(wand.save(new CompoundTag()));
        for(int i=0;i<13;i++){int a=WandEquipment.steps(wand),b=WandEquipment.steps(loaded);helper.assertTrue(a==b,"小数施法节奏在保存后连续");count+=a;}
        helper.assertTrue(count==30,"铜制杖端20个物理步推进30个法阵步");
        WorldSpells spells=new WorldSpells();spells.launch(null,helper.absoluteVec(new Vec3(3,3,3)),new Vec3(0,0,1),12,1,new Packet(Mixture.pure(Fixed.ONE).convert(Element.ARCANE,Fixed.ONE),SpellSpec.EMPTY),98304);
        CompoundTag current=spells.save(new CompoundTag());helper.assertTrue(WorldSpells.load(current).save(new CompoundTag()).equals(current),"在途法术冻结杖端强度，不依赖后续装备");
        CompoundTag legacy=current.copy();legacy.putInt("Version",1);legacy.getList("Flights",10).getCompound(0).remove("Strength");
        CompoundTag migrated=WorldSpells.load(legacy).save(new CompoundTag());helper.assertTrue(migrated.getInt("Version")==7 && migrated.getList("Flights",10).getCompound(0).getLong("Strength")==Fixed.ONE && migrated.getList("Flights",10).getCompound(0).getInt("Effects")==1,"旧世界弹道明确迁移为原强度及原实体效果");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void headStrengthAffectsDamage(GameTestHelper helper){
        var level=helper.getLevel();var normal=helper.spawn(EntityType.ZOMBIE,new BlockPos(2,1,5));var strong=helper.spawn(EntityType.ZOMBIE,new BlockPos(6,1,5));normal.setNoAi(true);strong.setNoAi(true);
        var cast=new MagicCircuit.Cast(0,Mixture.pure(Fixed.ONE).convert(Element.ARCANE,Fixed.ONE),new SpellSpec(SpellSpec.Method.DIRECTED,SpellSpec.Shape.BEAM,0,0,0,0));UUID id=new UUID(28,1);
        float beforeNormal=normal.getHealth(),beforeStrong=strong.getHealth();
        SpellEffects.cast(level,null,helper.absoluteVec(new Vec3(2.5,1.5,1.5)),new Vec3(0,0,1),id,cast,RuleResources.defaults(),Fixed.ONE);
        SpellEffects.cast(level,null,helper.absoluteVec(new Vec3(6.5,1.5,1.5)),new Vec3(0,0,1),id,cast,RuleResources.defaults(),98304);
        helper.assertTrue(beforeStrong-strong.getHealth()>beforeNormal-normal.getHealth() && normal.getHealth()<beforeNormal,"杖端强度改变真实服务器伤害");helper.succeed();
    }
}
