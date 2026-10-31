package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.server.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class ProjectileMinecraftContracts {
    private static void prepare(GameTestHelper h){for(int x=-16;x<=16;x+=16)for(int z=-16;z<=16;z+=16)h.getLevel().getChunkAt(h.absolutePos(new BlockPos(4,2,4)).offset(x,0,z));for(int x=0;x<=8;x++)for(int z=0;z<=8;z++)for(int y=1;y<=6;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);}
    private static Mixture mana(Element e,long n){long[] a=new long[17];a[e.component(false)]=n;return new Mixture(a);}
    private static SpellSpec sphere(ProjectileEffects e){return new SpellSpec(SpellSpec.Method.DIRECTED,SpellSpec.Shape.SPHERE,0,0,0,0,e);}
    private static CompoundTag flight(WorldSpells state){return state.save(new CompoundTag()).getList("Flights",Tag.TAG_COMPOUND).getCompound(0);}
    private static Cow cow(GameTestHelper h,BlockPos at){var e=h.spawn(EntityType.COW,at);e.setNoAi(true);e.setNoGravity(true);return e;}
    @GameTest(template="empty")
    public static void bounceSpendsBudgetAndRestores(GameTestHelper h){
        prepare(h);for(int y=1;y<=6;y++)h.setBlock(new BlockPos(2,y,4),Blocks.STONE);
        WorldSpells state=new WorldSpells();state.launch(null,h.absoluteVec(new Vec3(2.5,3.5,2.5)),new Vec3(0,0,1),8,1,new Packet(mana(Element.FIRE,4*Fixed.ONE),sphere(new ProjectileEffects(0,2,0))));
        for(int i=0;i<4 && flight(state).getInt("Bounces")==2;i++)state.tick(h.getLevel());var f=flight(state);
        h.assertTrue(f.getInt("Bounces")==1 && f.getDouble("DZ")==-1,"碰撞法线反射方向并消费一次弹跳："+f);
        h.assertTrue(JsonCodecs.readPacket(f.getString("Packet")).mana().total()==3*Fixed.ONE,"首次反射实际耗散25%预算");
        h.assertTrue(f.getDouble("Remaining")<6.5 && f.getDouble("Remaining")>6.4 && h.getBlockState(new BlockPos(2,3,4)).is(Blocks.STONE),"实际飞行距离和离面偏移消耗射程，不破坏反射面");
        var saved=state.save(new CompoundTag());var copy=WorldSpells.load(saved);h.assertTrue(copy.save(new CompoundTag()).equals(saved),"恢复保留反射次数、剩余射程及冻结规则");
        for(int i=0;i<3;i++){state.tick(h.getLevel());copy.tick(h.getLevel());h.assertTrue(state.save(new CompoundTag()).equals(copy.save(new CompoundTag())),"反射后恢复轨迹一致");}h.succeed();
    }
    @GameTest(template="empty")
    public static void homingSeesVisibleFrontTargetsAndTurnsGradually(GameTestHelper h){
        prepare(h);var visible=cow(h,new BlockPos(5,2,6));cow(h,new BlockPos(2,2,0));cow(h,new BlockPos(2,2,4));for(int y=1;y<=5;y++)h.setBlock(new BlockPos(2,y,3),Blocks.STONE);
        var state=new WorldSpells();state.launch(null,h.absoluteVec(new Vec3(2.5,2.8,1.5)),new Vec3(0,0,1),12,1,new Packet(mana(Element.FIRE,4*Fixed.ONE),sphere(new ProjectileEffects(0,0,8))));state.tick(h.getLevel());var f=flight(state);
        h.assertTrue(f.hasUUID("Target") && f.getUUID("Target").equals(visible.getUUID()),"追踪忽略背后及被墙遮挡的更近生物");
        double dot=f.getDouble("DZ");h.assertTrue(f.getDouble("DX")>0 && Math.acos(dot)<=Math.toRadians(15)+1e-9,"有限转向不瞬间命中或瞬间转向");
        h.assertTrue(JsonCodecs.readPacket(f.getString("Packet")).mana().total()==2*Fixed.ONE,"追踪在发射时预付一半作用量");
        var copy=WorldSpells.load(state.save(new CompoundTag()));visible.discard();state.tick(h.getLevel());copy.tick(h.getLevel());h.assertTrue(state.save(new CompoundTag()).equals(copy.save(new CompoundTag())),"目标消失与重选在恢复后保持一致");h.succeed();
    }
    @GameTest(template="empty")
    public static void homingActuallyHitsAfterSave(GameTestHelper h){
        prepare(h);var target=cow(h,new BlockPos(5,2,6));var state=new WorldSpells();state.launch(null,h.absoluteVec(new Vec3(2.5,2.8,1.5)),new Vec3(0,0,1),12,1,new Packet(mana(Element.LIGHTNING,4*Fixed.ONE),sphere(new ProjectileEffects(0,0,8))));
        for(int i=0;i<3;i++)state.tick(h.getLevel());var saved=state.save(new CompoundTag());h.assertTrue(saved.getList("Flights",10).size()==1,"转向仍经过真实在途阶段");var restored=WorldSpells.load(saved);
        for(int i=0;i<16;i++)restored.tick(h.getLevel());h.assertTrue(target.getHealth()<10,"恢复后的追踪弹道真实命中偏离直线的生物");h.succeed();
    }
    @GameTest(template="empty")
    public static void burstBeamSharesOneImpactBudget(GameTestHelper h){
        prepare(h);var first=cow(h,new BlockPos(2,2,4));var side=cow(h,new BlockPos(3,2,4));h.setBlock(new BlockPos(5,2,4),Blocks.DIAMOND_BLOCK);
        var spec=new SpellSpec(SpellSpec.Method.DIRECTED,SpellSpec.Shape.BEAM,0,0,0,0,new ProjectileEffects(3,0,0));
        SpellEffects.cast(h.getLevel(),null,h.absoluteVec(new Vec3(2.5,2.8,1.5)),new Vec3(0,0,1),new UUID(29,801),new MagicCircuit.Cast(0,mana(Element.LIGHTNING,2*Fixed.ONE),spec),RuleResources.defaults());
        h.assertTrue(first.getHealth()<10 && side.getHealth()<10,"爆裂光束在首次接触处影响侧面目标");
        h.assertTrue(Math.abs((10-first.getHealth())+(10-side.getHealth())-4)<.001,"多个目标共享一次预算，不在主命中后复制完整爆炸伤害");h.assertTrue(h.getBlockState(new BlockPos(5,2,4)).is(Blocks.DIAMOND_BLOCK),"元素爆裂不调用破坏地形的原版爆炸");h.succeed();
    }
    @GameTest(template="empty")
    public static void burstWaterCreatesOnlyPaidSources(GameTestHelper h){
        prepare(h);for(int x=0;x<=8;x++)for(int z=0;z<=8;z++)h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var state=new WorldSpells();state.launch(null,h.absoluteVec(new Vec3(4.5,1.2,3.5)),new Vec3(0,0,1),1,1,new Packet(mana(Element.WATER,2*Fixed.ONE),sphere(new ProjectileEffects(3,0,0))));
        state.tick(h.getLevel());state.tick(h.getLevel());int sources=0;for(int x=0;x<=8;x++)for(int z=0;z<=8;z++){var block=h.getBlockState(new BlockPos(x,1,z));if(block.is(Blocks.WATER) && block.getFluidState().isSource())sources++;}
        h.assertTrue(sources==2,"水爆裂仍按两点预算初始创造两个真实水源");h.succeed();
    }
    @GameTest(template="empty")
    public static void legacyFlightsAndUnloadedDerivatives(GameTestHelper h){
        prepare(h);var state=new WorldSpells();state.launch(null,h.absoluteVec(new Vec3(2.5,3.5,2.5)),new Vec3(0,0,1),8,1,new Packet(mana(Element.FIRE,Fixed.ONE),SpellSpec.EMPTY));var legacy=state.save(new CompoundTag());legacy.putInt("Version",4);var old=legacy.getList("Flights",10).getCompound(0);old.putInt("Effects",3);old.remove("Bounces");old.remove("ProjectileRules");
        var packet=com.google.gson.JsonParser.parseString(old.getString("Packet")).getAsJsonObject();packet.getAsJsonObject("spell").remove("effects");old.putString("Packet",packet.toString());var restored=WorldSpells.load(legacy);var migrated=flight(restored);
        h.assertTrue(migrated.getInt("Effects")==3 && migrated.getInt("Bounces")==0 && JsonCodecs.readPacket(migrated.getString("Packet")).spell().effects().empty(),"历史v4飞行显式迁移为空派生，不改变碰撞语义");
        var remote=new WorldSpells();remote.launch(null,new Vec3(2100000,100,2100000),new Vec3(0,0,1),8,1,new Packet(mana(Element.FIRE,Fixed.ONE),sphere(new ProjectileEffects(3,2,8))));var saved=remote.save(new CompoundTag());remote.tick(h.getLevel());h.assertTrue(remote.save(new CompoundTag()).equals(saved) && !h.getLevel().hasChunkAt(new BlockPos(2100000,100,2100000)),"卸载处不推进追踪/弹跳，不强加载世界");h.succeed();
    }
}
