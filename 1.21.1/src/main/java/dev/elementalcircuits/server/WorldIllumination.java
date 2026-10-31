package dev.elementalcircuits.server;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.RadianceResources;
import dev.elementalcircuits.block.DimmedLightBlock;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import java.util.*;

/** Loaded-world tick leases. Never restores over a block replaced by a player or another system. */
public final class WorldIllumination extends SavedData {
    private record Lease(BlockState expected,int remaining){ }
    private final Map<BlockPos,Lease> leases=new TreeMap<>(Comparator.<BlockPos>comparingInt(p->p.getX()).thenComparingInt(p->p.getY()).thenComparingInt(p->p.getZ()));
    private boolean invalid;private CompoundTag original;
    public static WorldIllumination get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(WorldIllumination::new,(t,r)->WorldIllumination.load(t)),"elementalcircuits_illumination");}
    public boolean canTrack(BlockPos pos){return !invalid && (leases.containsKey(pos) || leases.size()<RadianceResources.rules().maxLights());}
    public void adopt(BlockPos pos,BlockState expected,int life){
        if(life<1 || life>1200 || !marker(expected) || !canTrack(pos))throw new IllegalArgumentException("临时光照租期超限");
        Lease old=leases.get(pos);int remaining=old!=null && old.expected.equals(expected)?Math.max(old.remaining,life):life;
        leases.put(pos.immutable(),new Lease(expected,remaining));setDirty();
    }
    public void forget(BlockPos pos){if(leases.remove(pos)!=null)setDirty();}
    public boolean owns(BlockPos pos){return !invalid && leases.containsKey(pos);}
    private static boolean marker(BlockState s){return s.is(ElementalCircuits.SPELL_LIGHT.get()) || s.is(ElementalCircuits.DIMMED_LIGHT.get());}
    private static boolean neighborhoodLoaded(ServerLevel level,BlockPos pos){
        return SpellEffects.loadedBetween(level,Vec3.atCenterOf(pos.offset(-1,0,-1)),Vec3.atCenterOf(pos.offset(1,0,1)));
    }
    public boolean glow(ServerLevel level,UUID owner,BlockPos pos,int life){
        if(!canTrack(pos) || !ElementInteractions.permitted(level,owner,pos))return false;
        BlockState before=level.getBlockState(pos),after=ElementalCircuits.SPELL_LIGHT.get().defaultBlockState();
        if(!before.isAir() && !before.is(ElementalCircuits.SPELL_LIGHT.get()))return false;
        if(!neighborhoodLoaded(level,pos))return false;
        if(NeoForge.EVENT_BUS.post(new ElementBlockChangeEvent(BlockSnapshot.create(level.dimension(),level,pos),level.getBlockState(pos.below()),owner==null?null:level.getEntity(owner),after)).isCanceled())return false;
        if(!level.getBlockState(pos).equals(before) || !ElementInteractions.permitted(level,owner,pos))return false;
        if(!before.equals(after) && !level.setBlock(pos,after,3))return false;
        adopt(pos,after,life);return true;
    }
    public void tick(ServerLevel level){
        if(invalid || leases.isEmpty())return;boolean changed=false;
        Iterator<Map.Entry<BlockPos,Lease>> it=leases.entrySet().iterator();
        while(it.hasNext()){
            var entry=it.next();BlockPos pos=entry.getKey();Lease lease=entry.getValue();
            if(!level.hasChunkAt(pos))continue;
            if(!level.getBlockState(pos).equals(lease.expected)){it.remove();changed=true;continue;}
            if(lease.remaining<=1){if(!restore(level,pos,lease.expected))continue;it.remove();}
            else entry.setValue(new Lease(lease.expected,lease.remaining-1));changed=true;
        }
        if(changed)setDirty();
    }
    public void reconcile(ServerLevel level,BlockPos pos){
        if(!owns(pos)){BlockState current=level.getBlockState(pos);if(marker(current))restore(level,pos,current);}
    }
    private static boolean restore(ServerLevel level,BlockPos pos,BlockState marker){
        // Native neighbor updates must not pull an unloaded adjacent chunk into memory.
        if(!neighborhoodLoaded(level,pos))return false;
        BlockState restored=marker.is(ElementalCircuits.DIMMED_LIGHT.get())?DimmedLightBlock.original(marker):Blocks.AIR.defaultBlockState();
        if(restored.is(Blocks.REDSTONE_LAMP))restored=restored.setValue(RedstoneLampBlock.LIT,level.hasNeighborSignal(pos));
        level.setBlock(pos,restored,3);
        if(restored.getBlock() instanceof RedstoneTorchBlock)restored.handleNeighborChanged(level,pos,restored.getBlock(),pos,false);
        return true;
    }
    @Override public CompoundTag save(CompoundTag root,net.minecraft.core.HolderLookup.Provider registries){
        if(invalid)return original.copy();root.putInt("Version",1);ListTag list=new ListTag();
        for(var entry:leases.entrySet()){
            CompoundTag tag=new CompoundTag();BlockPos pos=entry.getKey();tag.putInt("X",pos.getX());tag.putInt("Y",pos.getY());tag.putInt("Z",pos.getZ());
            tag.put("State",NbtUtils.writeBlockState(entry.getValue().expected));tag.putInt("Remaining",entry.getValue().remaining);list.add(tag);
        }
        root.put("Leases",list);return root;
    }
    public static WorldIllumination load(CompoundTag root){
        WorldIllumination data=new WorldIllumination();
        try{
            if(!root.contains("Version",Tag.TAG_INT) || root.getInt("Version")!=1 || !root.contains("Leases",Tag.TAG_LIST))throw new IllegalArgumentException("未知临时光照版本");
            if(((ListTag)root.get("Leases")).getElementType()!=Tag.TAG_END && ((ListTag)root.get("Leases")).getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("临时光照列表类型损坏");
            ListTag list=root.getList("Leases",Tag.TAG_COMPOUND);if(list.size()>RadianceResources.rules().maxLights())throw new IllegalArgumentException("临时光照过多");
            for(int i=0;i<list.size();i++){
                CompoundTag tag=list.getCompound(i);for(String key:List.of("X","Y","Z","Remaining"))if(!tag.contains(key,Tag.TAG_INT))throw new IllegalArgumentException("临时光照缺字段");
                BlockPos pos=new BlockPos(tag.getInt("X"),tag.getInt("Y"),tag.getInt("Z"));int life=tag.getInt("Remaining");
                if(Math.abs((long)pos.getX())>30000000 || Math.abs((long)pos.getZ())>30000000 || Math.abs((long)pos.getY())>30000000 || life<1 || life>1200 || !tag.contains("State",Tag.TAG_COMPOUND))throw new IllegalArgumentException("临时光照边界损坏");
                BlockState state=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),tag.getCompound("State"));
                if(!marker(state) || !NbtUtils.writeBlockState(state).equals(tag.getCompound("State")) || data.leases.put(pos,new Lease(state,life))!=null)throw new IllegalArgumentException("临时光照状态损坏");
            }
        }catch(RuntimeException e){data.leases.clear();data.invalid=true;data.original=root.copy();org.slf4j.LoggerFactory.getLogger("ElementalCircuits").error("临时光照存档损坏，保留原文并停用：{}",e.getMessage());}
        return data;
    }
}
