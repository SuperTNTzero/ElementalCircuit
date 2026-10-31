package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.RuleResources;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.server.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class WorldInteractionContracts {
    private static Mixture mana(Element element,boolean inverse,long amount){long[] v=new long[17];v[element.component(inverse)]=amount;return new Mixture(v);}
    private static void cast(GameTestHelper h,Element e,boolean inverse,long amount,Vec3 origin,Vec3 look,boolean area){
        loadArena(h.getLevel(),BlockPos.containing(h.absoluteVec(origin)));
        SpellSpec spec=new SpellSpec(area?SpellSpec.Method.AREA:SpellSpec.Method.DIRECTED,area?SpellSpec.Shape.SPHERE:SpellSpec.Shape.BEAM,0,0,area?3:0,0);
        SpellEffects.cast(h.getLevel(),null,h.absoluteVec(origin),look,new UUID(29,4),new MagicCircuit.Cast(0,mana(e,inverse,amount),spec),RuleResources.defaults());
    }
    private static void loadArena(net.minecraft.server.level.ServerLevel level,BlockPos center){
        // Positive fixtures explicitly prepare neighboring chunks; production casts never load them.
        for(int x=-16;x<=16;x+=16)for(int z=-16;z<=16;z+=16)level.getChunkAt(center.offset(x,0,z));
    }
    private static void beam(GameTestHelper h,Element e,boolean inverse){cast(h,e,inverse,4*Fixed.ONE,new Vec3(2.5,1.2,1.5),new Vec3(0,0,1),false);}
    @GameTest(template="empty",timeoutTicks=40)
    public static void fireAndWaterCreateRealBlocks(GameTestHelper h){
        h.setBlock(new BlockPos(2,1,4),Blocks.STONE);h.setBlock(new BlockPos(2,0,3),Blocks.STONE);
        beam(h,Element.FIRE,false);h.assertTrue(h.getBlockState(new BlockPos(2,1,3)).getBlock() instanceof BaseFireBlock,"火光束必须生成真实火焰方块");
        beam(h,Element.WATER,false);h.assertTrue(h.getBlockState(new BlockPos(2,1,3)).isAir(),"水首先熄灭真实火焰");
        beam(h,Element.WATER,false);var water=h.getBlockState(new BlockPos(2,1,3));
        h.assertTrue(water.is(Blocks.WATER) && water.getFluidState().isSource(),"空位生成原版水源，不是水粒子");
        h.runAfterDelay(8,()->{h.assertTrue(!h.getBlockState(new BlockPos(2,1,2)).getFluidState().isEmpty()
            || !h.getBlockState(new BlockPos(1,1,3)).getFluidState().isEmpty(),"生成水源参与原版流动");h.succeed();});
    }
    @GameTest(template="empty")
    public static void campfireAndFarming(GameTestHelper h){
        BlockPos camp=new BlockPos(2,1,4);h.setBlock(camp,Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT,false));
        beam(h,Element.FIRE,false);h.assertTrue(h.getBlockState(camp).getValue(BlockStateProperties.LIT),"火点燃营火");
        beam(h,Element.WATER,false);h.assertTrue(!h.getBlockState(camp).getValue(BlockStateProperties.LIT),"水熄灭营火");
        BlockPos farm=new BlockPos(6,1,3);h.setBlock(farm,Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,0));
        for(int y=2;y<=6;y++)h.setBlock(new BlockPos(6,y,3),Blocks.AIR);
        cast(h,Element.WATER,false,4*Fixed.ONE,new Vec3(6.5,4,3.5),new Vec3(0,-1,0),false);
        h.assertTrue(h.getBlockState(farm).getValue(FarmBlock.MOISTURE)==7,"水湿润耕地，改变实际农业状态 state="+h.getBlockState(farm)+" above="+h.getBlockState(farm.above()));
        h.setBlock(farm.above(),Blocks.AIR);cast(h,Element.WATER,true,Fixed.ONE,new Vec3(6.5,4,3.5),new Vec3(0,-1,0),false);
        h.assertTrue(h.getBlockState(farm).getValue(FarmBlock.MOISTURE)==0,"反水干燥耕地");h.succeed();
    }
    @GameTest(template="empty")
    public static void heatingAndCoolingWater(GameTestHelper h){
        BlockPos target=new BlockPos(2,1,4);h.setBlock(target,Blocks.ICE);beam(h,Element.FIRE,false);
        h.assertTrue(h.getBlockState(target).is(Blocks.WATER),"火将已有冰融成水");
        beam(h,Element.FIRE,true);h.assertTrue(h.getBlockState(target).is(Blocks.FROSTED_ICE),"反火命中水源并冻结为会自然融化的霜冰");h.succeed();
    }
    @GameTest(template="empty")
    public static void windMovesItemsWithSharedBudget(GameTestHelper h){
        var level=h.getLevel();Vec3 point=h.absoluteVec(new Vec3(2.5,1.2,4.5));ItemEntity item=new ItemEntity(level,point.x,point.y,point.z,new ItemStack(Items.COBBLESTONE));item.setNoGravity(true);item.setDeltaMovement(Vec3.ZERO);level.addFreshEntity(item);
        beam(h,Element.WIND,false);h.assertTrue(item.getDeltaMovement().z>0,"风推动真实掉落物");
        item.setDeltaMovement(Vec3.ZERO);beam(h,Element.WIND,true);h.assertTrue(item.getDeltaMovement().z<0,"反风吸回真实掉落物");item.discard();
        List<ItemEntity> items=new ArrayList<>();
        for(double x:new double[]{2.5,4.5}){Vec3 p=h.absoluteVec(new Vec3(x,2,3.5));ItemEntity i=new ItemEntity(level,p.x,p.y,p.z,new ItemStack(Items.STICK));i.setNoGravity(true);i.setDeltaMovement(Vec3.ZERO);level.addFreshEntity(i);items.add(i);}
        cast(h,Element.WIND,false,Fixed.ONE,new Vec3(3.5,2,3.5),new Vec3(0,0,1),true);
        double sum=items.stream().mapToDouble(i->i.getDeltaMovement().length()).sum();
        h.assertTrue(Math.abs(sum-0.25)<0.00001 && items.get(0).getDeltaMovement().x<0 && items.get(1).getDeltaMovement().x>0,"两件物品均分同一份风预算，不各领完整冲量");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void lightningRodAndCopper(GameTestHelper h){
        BlockPos rod=new BlockPos(2,1,4);h.setBlock(rod,Blocks.LIGHTNING_ROD.defaultBlockState().setValue(LightningRodBlock.FACING,Direction.UP));h.setBlock(rod.below(),Blocks.STONE);
        beam(h,Element.LIGHTNING,false);h.assertTrue(h.getBlockState(rod).getValue(LightningRodBlock.POWERED),"雷激活避雷针实际红石脉冲");
        h.assertTrue(h.getLevel().getBestNeighborSignal(h.absolutePos(rod.below()))==15,"脉冲接入原版红石");
        beam(h,Element.LIGHTNING,true);h.assertTrue(!h.getBlockState(rod).getValue(LightningRodBlock.POWERED),"反雷主动泄放脉冲");
        beam(h,Element.LIGHTNING,false);
        BlockPos copper=new BlockPos(6,1,4);h.setBlock(copper,Blocks.OXIDIZED_COPPER);
        cast(h,Element.LIGHTNING,false,Fixed.ONE,new Vec3(6.5,1.5,1.5),new Vec3(0,0,1),false);
        h.assertTrue(h.getBlockState(copper).is(Blocks.WEATHERED_COPPER),"雷清理一层实际铜锈");
        h.runAfterDelay(10,()->{h.assertTrue(!h.getBlockState(rod).getValue(LightningRodBlock.POWERED),"避雷针脉冲由原版固定步结束");h.succeed();});
    }
    @GameTest(template="empty")
    public static void sourceCostAndPlacementCancellation(GameTestHelper h){
        BlockPos wall=new BlockPos(2,1,4),source=new BlockPos(2,1,3);h.setBlock(wall,Blocks.STONE);h.setBlock(source,Blocks.AIR);
        cast(h,Element.WATER,false,Fixed.ONE/2,new Vec3(2.5,1.2,1.5),new Vec3(0,0,1),false);
        h.assertTrue(h.getBlockState(source).isAir(),"不到1点生成预算不放置水源");
        Consumer<BlockEvent.EntityPlaceEvent> cancel=event->{if(event.getPos().equals(h.absolutePos(source)))event.setCanceled(true);};
        NeoForge.EVENT_BUS.addListener(cancel);
        BlockPos lava=source.east();h.setBlock(lava,Blocks.LAVA);
        try{beam(h,Element.WATER,false);h.assertTrue(h.getBlockState(source).isAir() && h.getBlockState(lava).is(Blocks.LAVA),"放置预检取消后不写入水，也不提前触发岩浆反应");}
        finally{NeoForge.EVENT_BUS.unregister(cancel);}
        h.setBlock(lava,Blocks.AIR);
        h.setBlock(source,Blocks.CHEST);beam(h,Element.WATER,false);h.assertTrue(h.getBlockState(source).is(Blocks.CHEST),"生成不覆盖容器");h.succeed();
    }
    @GameTest(template="empty")
    public static void areaSourcesRemainFinite(GameTestHelper h){
        for(int x=1;x<=6;x++)for(int z=1;z<=6;z++){h.setBlock(new BlockPos(x,0,z),Blocks.STONE);h.setBlock(new BlockPos(x,1,z),Blocks.AIR);}
        cast(h,Element.WATER,false,2*Fixed.ONE,new Vec3(3.5,1.5,3.5),new Vec3(0,0,1),true);
        int sources=0;for(int x=0;x<=7;x++)for(int z=0;z<=7;z++)if(h.getBlockState(new BlockPos(x,1,z)).is(Blocks.WATER) && h.getBlockState(new BlockPos(x,1,z)).getFluidState().isSource())sources++;
        h.assertTrue(sources>0 && sources<=2,"2点范围预算最多创造两个原始水源");h.succeed();
    }
    @GameTest(template="empty")
    public static void nativeWaterLavaReaction(GameTestHelper h){
        BlockPos source=new BlockPos(2,1,3),lava=source.east();
        h.setBlock(new BlockPos(2,1,4),Blocks.STONE);h.setBlock(source,Blocks.AIR);h.setBlock(lava,Blocks.LAVA);
        beam(h,Element.WATER,false);
        h.assertTrue(h.getBlockState(source).is(Blocks.WATER) && h.getBlockState(lava).is(Blocks.OBSIDIAN),"生成水源真实参与原版水/岩浆反应");h.succeed();
    }
    @GameTest(template="empty")
    public static void areaObstruction(GameTestHelper h){
        BlockPos visible=new BlockPos(1,1,2),hidden=new BlockPos(3,1,4);
        h.setBlock(visible,Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT,false));
        h.setBlock(hidden,Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT,false));
        h.setBlock(new BlockPos(3,1,3),Blocks.STONE);
        cast(h,Element.FIRE,false,64*Fixed.ONE,new Vec3(3.5,1.5,1.5),new Vec3(0,0,1),true);
        h.assertTrue(h.getBlockState(visible).getValue(BlockStateProperties.LIT) && !h.getBlockState(hidden).getValue(BlockStateProperties.LIT),"范围火能点燃可见营火，不能穿墙点燃隐藏营火");h.succeed();
    }
    @GameTest(template="empty")
    public static void projectileWorldEffectsVersion(GameTestHelper h){
        BlockPos source=new BlockPos(2,1,3);h.setBlock(new BlockPos(2,1,4),Blocks.STONE);h.setBlock(source.below(),Blocks.STONE);h.setBlock(source,Blocks.AIR);
        WorldSpells current=new WorldSpells();current.launch(null,h.absoluteVec(new Vec3(2.5,1.2,1.5)),new Vec3(0,0,1),8,1,new Packet(mana(Element.FIRE,false,4*Fixed.ONE),SpellSpec.EMPTY));
        var old=current.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess());old.putInt("Version",2);old.getList("Flights",10).getCompound(0).remove("Effects");
        WorldSpells legacy=WorldSpells.load(old);
        h.assertTrue(legacy.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()).getList("Flights",10).getCompound(0).getInt("Effects")==1,"旧弹道保留实体效果协议");
        for(int i=0;i<5;i++)legacy.tick(h.getLevel());h.assertTrue(h.getBlockState(source).isAir(),"旧在途弹道升级后不会新增地形点火");
        WorldSpells restored=WorldSpells.load(current.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()));
        for(int i=0;i<5;i++)restored.tick(h.getLevel());
        h.assertTrue(h.getBlockState(source).getBlock() instanceof BaseFireBlock,"新弹道保存恢复后仍能点燃真实世界");h.succeed();
    }
    @GameTest(template="empty")
    public static void netherWaterEvaporates(GameTestHelper h){
        var nether=h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);h.assertTrue(nether!=null,"测试服务器应有下界");
        BlockPos anchor=h.absolutePos(new BlockPos(2,1,4));BlockPos wall=new BlockPos(anchor.getX(),80,anchor.getZ()),source=wall.north();loadArena(nether,wall);
        Map<BlockPos,net.minecraft.world.level.block.state.BlockState> old=new HashMap<>();
        for(int i=0;i<4;i++){BlockPos pos=wall.north(i);old.put(pos,nether.getBlockState(pos));}
        java.util.concurrent.atomic.AtomicBoolean observed=new java.util.concurrent.atomic.AtomicBoolean();
        Consumer<BlockEvent.EntityPlaceEvent> observe=event->{if(event.getLevel()==nether && event.getPos().equals(source))observed.set(true);};
        NeoForge.EVENT_BUS.addListener(observe);
        try{
            nether.setBlock(wall,Blocks.STONE.defaultBlockState(),3);for(int i=1;i<4;i++)nether.setBlock(wall.north(i),Blocks.AIR.defaultBlockState(),3);
            var spell=new SpellSpec(SpellSpec.Method.DIRECTED,SpellSpec.Shape.BEAM,0,0,0,0);
            SpellEffects.cast(nether,null,Vec3.atCenterOf(wall.north(3)).add(0,-0.3,0),new Vec3(0,0,1),new UUID(29,8),new MagicCircuit.Cast(0,mana(Element.WATER,false,4*Fixed.ONE),spell),RuleResources.defaults());
            h.assertTrue(observed.get() && nether.getBlockState(source).isAir(),"下界确实接收生成作用但蒸发，不生成水源 observed="+observed.get()+" state="+nether.getBlockState(source));
        }finally{NeoForge.EVENT_BUS.unregister(observe);old.forEach((pos,state)->nether.setBlock(pos,state,3));}
        h.succeed();
    }
    @GameTest(template="empty")
    public static void worldSpellsDoNotLoadRemoteChunks(GameTestHelper h){
        var level=h.getLevel();BlockPos remote=new BlockPos(2000000,100,2000000);
        h.assertTrue(!level.hasChunkAt(remote),"隔离远端区块初始未加载");
        var spell=new SpellSpec(SpellSpec.Method.DIRECTED,SpellSpec.Shape.BEAM,0,0,0,0);
        SpellEffects.cast(level,null,Vec3.atCenterOf(remote),new Vec3(1,0,0),new UUID(29,9),new MagicCircuit.Cast(0,mana(Element.WATER,false,4*Fixed.ONE),spell),RuleResources.defaults());
        h.assertTrue(!level.hasChunkAt(remote) && !level.hasChunkAt(remote.east(16)),"光束不为查询或放水强制加载区块");h.succeed();
    }
    @GameTest(template="empty")
    public static void mixedPureManaDoesNotHideWorldComponents(GameTestHelper h){
        BlockPos source=new BlockPos(2,1,3);h.setBlock(new BlockPos(2,1,4),Blocks.STONE);h.setBlock(source.below(),Blocks.STONE);h.setBlock(source,Blocks.AIR);
        loadArena(h.getLevel(),h.absolutePos(source));
        Mixture mixed=Mixture.pure(4*Fixed.ONE).plus(mana(Element.FIRE,false,Fixed.ONE));
        var spell=new SpellSpec(SpellSpec.Method.DIRECTED,SpellSpec.Shape.BEAM,0,0,0,0);
        SpellEffects.cast(h.getLevel(),null,h.absoluteVec(new Vec3(2.5,1.2,1.5)),new Vec3(0,0,1),new UUID(29,10),new MagicCircuit.Cast(0,mixed,spell),RuleResources.defaults());
        h.assertTrue(h.getBlockState(source).getBlock() instanceof BaseFireBlock,"纯净占多数时，已支付的火成分仍能生成火焰");h.succeed();
    }
}
