package dev.elementalcircuits.server;

import dev.elementalcircuits.adapter.ManaFieldResources;
import dev.elementalcircuits.adapter.persistence.ManaFieldPersistence;
import dev.elementalcircuits.block.ManaNodeBlockEntity;
import dev.elementalcircuits.domain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Per-dimension finite field. No chunk loading, wall-clock catch-up or client mutation. */
public final class WorldManaField extends SavedData {
    private ManaField field;
    private CompoundTag invalid;
    private final NavigableSet<ManaField.Cell> active=new TreeSet<>();
    private final Map<Long,List<ManaField.Cell>> byChunk=new HashMap<>();
    private final NavigableMap<BlockPos,ManaNodeBlockEntity> nodes=new TreeMap<>();
    private long preparedTick=Long.MIN_VALUE;
    public WorldManaField(){field=new ManaField(ManaFieldResources.defaults());}
    public static WorldManaField get(ServerLevel level){return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(WorldManaField::new,(t,r)->load(t)),"elementalcircuits_mana_field");}
    public ManaField field(){if(invalid!=null)throw new IllegalArgumentException("魔力场数据损坏，已保留并停用");return field;}
    public boolean valid(){return invalid==null;}
    private static long chunk(ManaField.Cell c){return net.minecraft.world.level.ChunkPos.asLong(Math.floorDiv(c.x(),2),Math.floorDiv(c.z(),2));}
    private void index(ManaField.Cell c){byChunk.computeIfAbsent(chunk(c),k->new ArrayList<>()).add(c);}
    public void loaded(int x,int z){if(invalid==null)active.addAll(byChunk.getOrDefault(net.minecraft.world.level.ChunkPos.asLong(x,z),List.of()));}
    public void unloaded(int x,int z){active.removeAll(byChunk.getOrDefault(net.minecraft.world.level.ChunkPos.asLong(x,z),List.of()));}
    private static boolean loaded(ServerLevel level,ManaField.Cell c){return c.y()*8>=level.getMinBuildHeight() && c.y()*8<level.getMaxBuildHeight() && level.hasChunk(Math.floorDiv(c.x(),2),Math.floorDiv(c.z(),2));}
    public ManaField.Cell discover(ServerLevel level,BlockPos pos){
        ManaField.Cell center=ManaField.Cell.at(pos.getX(),pos.getY(),pos.getZ());
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++){
            if(Math.abs((long)center.x()+dx)>3750000 || Math.abs((long)center.z()+dz)>3750000 || center.y()+dy < -512 || center.y()+dy>511)continue;
            ManaField.Cell c=center.offset(dx,dy,dz);if(!loaded(level,c))continue;
            if(field().cells().size()>=ManaField.MAX_CELLS && !field().cells().contains(c))continue;
            if(field().discover(c)){index(c);setDirty();}active.add(c);
        }
        return center;
    }
    public void register(ManaNodeBlockEntity node){if(invalid==null)nodes.put(node.getBlockPos().immutable(),node);}
    public void unregister(ManaNodeBlockEntity node){nodes.remove(node.getBlockPos(),node);}
    public ManaSource tap(ServerLevel level,BlockPos pos){
        if(invalid!=null)return ManaSource.NONE;
        ManaField.Cell cell=discover(level,pos);long quota=field.rules().intakePerTick();
        return new ManaSource(){long remaining=quota;
            public long available(){return Math.min(remaining,field.amount(cell));}
            public boolean debit(long amount){Fixed.checked(amount);if(amount>remaining || !loaded(level,cell))return false;if(!field.debit(cell,amount))return false;remaining-=amount;if(amount>0)setDirty();return true;}
        };
    }
    public void tick(ServerLevel level){
        if(invalid!=null || preparedTick==level.getGameTime())return;preparedTick=level.getGameTime();
        nodes.entrySet().removeIf(e->e.getValue().isRemoved() || !level.hasChunkAt(e.getKey()));
        // Node activation precedes diffusion. Sinks run later in WorldCircuits' stable host order.
        if(field.phase()==0 || active.isEmpty())for(var node:List.copyOf(nodes.values()))if(node.usable())discover(level,node.getBlockPos());
        // Between diffusion steps only advance the phase: do not copy or scan the entire active field at 20 Hz.
        if(!active.isEmpty() && field.phase()+1<field.rules().stepTicks()){
            field.tick(List.of(active.first()));setDirty();return;
        }
        active.removeIf(c->!loaded(level,c));
        List<ManaField.Cell> all=new ArrayList<>(active),budget=all;
        if(all.size()>ManaField.MAX_ACTIVE){
            // Minecraft persists gameTime; deriving the cursor avoids a hidden, unsaved continuation state.
            int cursor=(int)(Math.floorMod(level.getGameTime()/field.rules().stepTicks(),(long)all.size())*ManaField.MAX_ACTIVE%all.size());
            budget=new ArrayList<>(ManaField.MAX_ACTIVE);
            for(int i=0;i<ManaField.MAX_ACTIVE;i++)budget.add(all.get((cursor+i)%all.size()));
        }
        Object before=field.changeToken();boolean due=field.tick(budget);
        Set<ManaField.Cell> selected=due?new HashSet<>(budget):Set.of();
        if(due)for(var node:List.copyOf(nodes.values())){
            if(!node.usable())continue;ManaField.Cell c=ManaField.Cell.at(node.getBlockPos().getX(),node.getBlockPos().getY(),node.getBlockPos().getZ());
            // Sources outside this step's bounded cell set freeze too, rather than overfilling inactive regions.
            if(!selected.contains(c))continue;long accepted=field.inject(c,node.emission());if(accepted>0)node.emitted(accepted);
        }
        if(field.changeToken()!=before)setDirty();
    }
    public static WorldManaField load(CompoundTag tag){
        WorldManaField data=new WorldManaField();try{data.field=ManaFieldPersistence.read(tag);for(var c:data.field.cells())data.index(c);}
        catch(RuntimeException error){data.invalid=tag.copy();org.slf4j.LoggerFactory.getLogger("ElementalCircuits").error("魔力场存档校验失败，保留并停用：{}",error.getMessage());}return data;
    }
    @Override public CompoundTag save(CompoundTag tag,net.minecraft.core.HolderLookup.Provider registries){if(invalid!=null){tag.merge(invalid);return tag;}tag.merge(ManaFieldPersistence.write(field));return tag;}
}
