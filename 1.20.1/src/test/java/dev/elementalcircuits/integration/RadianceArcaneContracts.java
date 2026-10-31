package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.block.DimmedLightBlock;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.server.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class RadianceArcaneContracts {
    private static Mixture mana(Element element,long amount){long[] v=new long[17];v[element.component(false)]=amount;return new Mixture(v);}
    private static void prepare(GameTestHelper h){
        var level=h.getLevel();BlockPos p=h.absolutePos(new BlockPos(4,2,4));
        for(int x=-16;x<=16;x+=16)for(int z=-16;z<=16;z+=16)level.getChunkAt(p.offset(x,0,z));
        for(int x=1;x<9;x++)for(int z=1;z<9;z++)for(int y=1;y<7;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
    }
    private static void beam(GameTestHelper h,Mixture mana){
        SpellEffects.cast(h.getLevel(),null,h.absoluteVec(new Vec3(2.5,2.5,1.5)),new Vec3(0,0,1),new UUID(29,100),new MagicCircuit.Cast(0,mana,new SpellSpec(SpellSpec.Method.DIRECTED,SpellSpec.Shape.BEAM,8,0,1,0)),RuleResources.defaults());
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void lightCreatesRealLightAndTrail(GameTestHelper h){
        prepare(h);h.setBlock(new BlockPos(2,2,4),Blocks.STONE);beam(h,mana(Element.LIGHT,4*Fixed.ONE));
        BlockPos source=new BlockPos(2,2,3),trail=new BlockPos(2,2,1);
        h.assertTrue(h.getBlockState(source).is(ElementalCircuits.SPELL_LIGHT.get()),"光在命中处创建临时光源");
        h.assertTrue(h.getBlockState(trail).is(ElementalCircuits.SPELL_LIGHT.get()),"光束路径产生真实光源，不只是亮粒子");
        h.assertTrue(h.getBlockState(source).getLightEmission()==15,"光源实际发出15级方块光");
        h.runAfterDelay(5,()->{
            h.assertTrue(h.getLevel().getBrightness(LightLayer.BLOCK,h.absolutePos(source.above()))>=13,"光照引擎实际照亮邻格");
            h.assertTrue(h.getBlockState(trail).isAir() && h.getBlockState(source).is(ElementalCircuits.SPELL_LIGHT.get()),"弹道短光清理，命中处长光继续存在");h.succeed();
        });
    }
    @GameTest(template="empty")
    public static void leaseExpiryPersistenceAndReplacement(GameTestHelper h){
        prepare(h);var level=h.getLevel();BlockPos p=h.absolutePos(new BlockPos(4,3,4));WorldIllumination data=new WorldIllumination();
        h.assertTrue(data.glow(level,null,p,4),"可放置短寿命光");data.tick(level);
        var snapshot=data.save(new CompoundTag());WorldIllumination restored=WorldIllumination.load(snapshot);
        h.assertTrue(restored.save(new CompoundTag()).equals(snapshot),"剩余寿命完整保存恢复");
        restored.tick(level);restored.tick(level);h.assertTrue(level.getBlockState(p).is(ElementalCircuits.SPELL_LIGHT.get()),"恢复不提前灭光");
        restored.tick(level);h.assertTrue(level.getBlockState(p).isAir(),"到期清理真实方块");
        WorldIllumination overlapping=new WorldIllumination();overlapping.glow(level,null,p,4);overlapping.tick(level);overlapping.glow(level,null,p,1);
        overlapping.tick(level);overlapping.tick(level);h.assertTrue(level.getBlockState(p).is(ElementalCircuits.SPELL_LIGHT.get()),"重叠短光不能缩短已有光源寿命");overlapping.tick(level);h.assertTrue(level.getBlockState(p).isAir(),"重叠租期最终正常清理");
        data=new WorldIllumination();data.glow(level,null,p,1);level.setBlock(p,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);data.tick(level);
        h.assertTrue(level.getBlockState(p).is(Blocks.DIAMOND_BLOCK),"清理不会覆盖玩家之后替换的方块");
        BlockPos remote=new BlockPos(2100000,100,2100000);data.adopt(remote,ElementalCircuits.SPELL_LIGHT.get().defaultBlockState(),4);
        var before=data.save(new CompoundTag());data.tick(level);h.assertTrue(data.save(new CompoundTag()).equals(before) && !level.hasChunkAt(remote),"卸载租期冻结且不加载远端区块");h.succeed();
    }
    @GameTest(template="empty")
    public static void shadowExtinguishesTorchesAndRestores(GameTestHelper h){
        prepare(h);BlockPos p=new BlockPos(2,2,4);h.setBlock(p.below(),Blocks.STONE);h.setBlock(p,Blocks.TORCH);
        beam(h,mana(Element.SHADOW,Fixed.ONE));BlockState dimmed=h.getBlockState(p);
        h.assertTrue(dimmed.is(ElementalCircuits.DIMMED_LIGHT.get()) && dimmed.getLightEmission()==0,"暗可瞄准无碰撞火把并熄灭实际光源");
        h.assertTrue(Block.getDrops(dimmed,h.getLevel(),h.absolutePos(p),null).stream().anyMatch(stack->stack.is(Items.TORCH)),"压暗火把破坏仍掉原火把，不复制新光源");
        WorldIllumination shortLease=new WorldIllumination();shortLease.adopt(h.absolutePos(p),dimmed,2);shortLease.tick(h.getLevel());shortLease.tick(h.getLevel());
        h.assertTrue(h.getBlockState(p).is(Blocks.TORCH),"压暗到期恢复原光源");h.succeed();
    }
    @GameTest(template="empty")
    public static void shadowFireAndOrphanRecovery(GameTestHelper h){
        prepare(h);BlockPos p=new BlockPos(2,2,4);h.setBlock(p,Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT,true));
        SpellEffects.cast(h.getLevel(),null,h.absoluteVec(new Vec3(2.5,2.2,1.5)),new Vec3(0,0,1),new UUID(29,101),new MagicCircuit.Cast(0,mana(Element.SHADOW,Fixed.ONE),new SpellSpec(SpellSpec.Method.DIRECTED,SpellSpec.Shape.BEAM,8,0,1,0)),RuleResources.defaults());
        h.assertTrue(!h.getBlockState(p).getValue(BlockStateProperties.LIT),"暗真正熄灭营火");
        var light=WorldIllumination.get(h.getLevel());BlockPos glow=h.absolutePos(new BlockPos(2,2,3));light.glow(h.getLevel(),null,glow,200);
        beam(h,mana(Element.SHADOW,Fixed.ONE));h.assertTrue(h.getLevel().getBlockState(glow).isAir() && !light.owns(glow),"暗清除法术光及其租期");
        BlockPos orphan=h.absolutePos(new BlockPos(6,2,4));var original=Blocks.OCHRE_FROGLIGHT.defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.X);var mask=DimmedLightBlock.mask(original);
        h.getLevel().setBlock(orphan,mask,3);light.reconcile(h.getLevel(),orphan);
        h.assertTrue(h.getLevel().getBlockState(orphan).equals(original),"光照数据缺失时可从区块状态恢复原光源和朝向");
        var wall=Blocks.SOUL_WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.EAST);
        h.assertTrue(DimmedLightBlock.original(DimmedLightBlock.mask(wall)).equals(wall),"壁挂火把朝向往返");h.succeed();
    }
    @GameTest(template="empty")
    public static void illuminationRespectsCancellation(GameTestHelper h){
        prepare(h);BlockPos p=h.absolutePos(new BlockPos(2,2,4));h.getLevel().setBlock(p,Blocks.GLOWSTONE.defaultBlockState(),3);
        Consumer<BlockEvent.EntityPlaceEvent> cancel=e->{if(e.getPos().equals(p))e.setCanceled(true);};MinecraftForge.EVENT_BUS.addListener(cancel);
        try{beam(h,mana(Element.SHADOW,Fixed.ONE));h.assertTrue(h.getLevel().getBlockState(p).is(Blocks.GLOWSTONE),"暗尊重方块修改取消事件");}
        finally{MinecraftForge.EVENT_BUS.unregister(cancel);}
        BlockPos air=p.above();Consumer<BlockEvent.EntityPlaceEvent> deny=e->{if(e.getPos().equals(air))e.setCanceled(true);};MinecraftForge.EVENT_BUS.addListener(deny);
        try{h.assertTrue(!WorldIllumination.get(h.getLevel()).glow(h.getLevel(),null,air,3) && h.getLevel().getBlockState(air).isAir(),"弹道照明同样尊重修改取消");}
        finally{MinecraftForge.EVENT_BUS.unregister(deny);}h.succeed();
    }
    @GameTest(template="empty")
    public static void arcaneBeamPassesBlocksWithoutPassingOtherElements(GameTestHelper h){
        prepare(h);var zombie=h.spawn(EntityType.ZOMBIE,new BlockPos(2,2,6));zombie.setNoAi(true);zombie.setNoGravity(true);h.setBlock(new BlockPos(2,2,4),Blocks.STONE);
        beam(h,mana(Element.FIRE,Fixed.ONE));h.assertTrue(zombie.getHealth()==20,"普通火被实体方块阻挡");
        long[] inverse=new long[17];inverse[Element.ARCANE.component(true)]=Fixed.ONE;beam(h,new Mixture(inverse));h.assertTrue(zombie.getHealth()==20,"反奥术保留普通碰撞");
        beam(h,mana(Element.FIRE,Fixed.ONE).plus(mana(Element.ARCANE,Fixed.ONE)));
        h.assertTrue(Math.abs(zombie.getHealth()-18)<0.001 && !zombie.isOnFire(),"混合火与奥术仅奥术成分穿墙造成2点伤害");
        h.assertTrue(h.getBlockState(new BlockPos(2,2,4)).is(Blocks.STONE),"奥术穿透不破坏方块");h.succeed();
    }
    @GameTest(template="empty")
    public static void phasedProjectileSaveAndLegacyCollision(GameTestHelper h){
        prepare(h);h.setBlock(new BlockPos(2,2,4),Blocks.STONE);var zombie=h.spawn(EntityType.ZOMBIE,new BlockPos(2,2,6));zombie.setNoAi(true);zombie.setNoGravity(true);
        WorldSpells spells=new WorldSpells();spells.launch(null,h.absoluteVec(new Vec3(2.5,2.5,1.5)),new Vec3(0,0,1),8,1,new Packet(mana(Element.ARCANE,Fixed.ONE),SpellSpec.EMPTY));
        CompoundTag old=spells.save(new CompoundTag());old.putInt("Version",3);old.getList("Flights",10).getCompound(0).putInt("Effects",2);old.getList("Flights",10).getCompound(0).remove("Trail");
        WorldSpells legacy=WorldSpells.load(old);for(int i=0;i<10;i++)legacy.tick(h.getLevel());h.assertTrue(zombie.getHealth()==20,"旧v3在途奥术维持原碰撞，不静默赋予穿透");
        spells.tick(h.getLevel());WorldSpells restored=WorldSpells.load(spells.save(new CompoundTag()));for(int i=0;i<10;i++)restored.tick(h.getLevel());
        h.assertTrue(zombie.getHealth()<20 && h.getBlockState(new BlockPos(2,2,4)).is(Blocks.STONE),"新奥术弹道恢复后穿墙命中");h.succeed();
    }
    @GameTest(template="empty")
    public static void movingLightPersistsPrepaidTrail(GameTestHelper h){
        prepare(h);WorldSpells spells=new WorldSpells();spells.launch(null,h.absoluteVec(new Vec3(2.5,2.5,1.5)),new Vec3(0,0,1),8,1,new Packet(mana(Element.LIGHT,4*Fixed.ONE),SpellSpec.EMPTY));
        spells.tick(h.getLevel());var saved=spells.save(new CompoundTag());var flight=saved.getList("Flights",10).getCompound(0);
        h.assertTrue(h.getBlockState(new BlockPos(2,2,2)).is(ElementalCircuits.SPELL_LIGHT.get()),"飞行弹道在当前格实际发光");
        long impact=JsonCodecs.readPacket(flight.getString("Packet")).mana().total();h.assertTrue(impact==3*Fixed.ONE && flight.getInt("Trail")==63,"弹道光已从光预算扣除且逐步消费预付时间");
        WorldSpells restored=WorldSpells.load(saved);h.assertTrue(restored.save(new CompoundTag()).equals(saved),"弹道照明剩余预算保存不重置");h.succeed();
    }
    @GameTest(template="empty")
    public static void phaseStillRespectsMagicShields(GameTestHelper h){
        prepare(h);var zombie=h.spawn(EntityType.ZOMBIE,new BlockPos(2,2,6));zombie.setNoAi(true);zombie.setNoGravity(true);
        WorldSpells spells=new WorldSpells();spells.launch(new UUID(29,111),h.absoluteVec(new Vec3(2.5,2.5,1.5)),new Vec3(0,0,1),8,1,new Packet(mana(Element.ARCANE,Fixed.ONE),SpellSpec.EMPTY));
        spells.shield(new UUID(29,112),h.absoluteVec(new Vec3(2.5,2.5,4)),new Vec3(0,0,-1),2,4*Fixed.ONE);
        for(int i=0;i<10;i++)spells.tick(h.getLevel());h.assertTrue(zombie.getHealth()==20,"奥术穿方块但仍被有容量的法术盾拦截");h.succeed();
    }
}
