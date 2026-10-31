package dev.elementalcircuits.block;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.domain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Fixed carrier owns its entire assembly and finite charge. No automatic or offline refill. */
public final class CircuitBlockEntity extends BlockEntity implements ManaSource {
    private ItemStack carrier;
    private CompoundTag invalid;
    public CircuitBlockEntity(BlockPos pos,BlockState state){super(ElementalCircuits.CIRCUIT_ENTITY.get(),pos,state);carrier=new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get());}
    public ItemStack carrier(){if(invalid!=null)throw new IllegalArgumentException("固定法阵数据损坏，已保留原始数据");return carrier;}
    public CircuitAssembly assembly(){return dev.elementalcircuits.adapter.persistence.CarrierRepository.load(carrier());}
    public void refreshMaterial(){if(level!=null && !level.isClientSide){var material=BoardMaterial.of(assembly().circuit(1).definition().rules().material());if(getBlockState().getValue(CircuitBlock.MATERIAL)!=material)level.setBlock(worldPosition,getBlockState().setValue(CircuitBlock.MATERIAL,material),3);}}
    public UUID owner(){var t=store();return t.hasUUID("Owner")?t.getUUID("Owner"):new UUID(0,0);}
    public void placed(ItemStack stack,Player player){if(invalid!=null)throw new IllegalArgumentException("原始法阵数据异常，拒绝覆盖");var candidate=stack.copy();candidate.setCount(1);candidate.remove(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);available(candidate);var data=fixedStore(candidate);data.putUUID("Owner",player.getUUID());dev.elementalcircuits.adapter.persistence.ItemData.put(candidate,"FixedCircuit",data);carrier=candidate;FixedCircuitAppearance.update(carrier);refreshMaterial();setChanged();}
    public boolean owns(ServerPlayer player){return player.getUUID().equals(owner()) && player.getAbilities().mayBuild && player.serverLevel()==level && level.mayInteract(player,worldPosition);}
    public boolean allowed(ServerPlayer player){return owns(player) && player.distanceToSqr(Vec3.atCenterOf(worldPosition))<=64;}
    private static CompoundTag fixedStore(ItemStack stack){if(dev.elementalcircuits.adapter.persistence.ItemData.read(stack)!=null && dev.elementalcircuits.adapter.persistence.ItemData.read(stack).contains("FixedCircuit") && !dev.elementalcircuits.adapter.persistence.ItemData.read(stack).contains("FixedCircuit",Tag.TAG_COMPOUND))throw new IllegalArgumentException("固定法阵供能根记录格式损坏");var t=dev.elementalcircuits.adapter.persistence.ItemData.read(stack).getCompound("FixedCircuit");if(t.contains("Owner") && !t.hasUUID("Owner"))throw new IllegalArgumentException("固定法阵所有者格式损坏");return t;}
    private CompoundTag store(){return fixedStore(carrier());}
    private static long counter(CompoundTag t,String key){if(t.contains(key) && !t.contains(key,Tag.TAG_LONG))throw new IllegalArgumentException("固定法阵计数格式损坏");long value=t.getLong(key);if(value<0)throw new IllegalArgumentException("固定法阵计数非法");return value;}
    private static long available(ItemStack stack){var t=fixedStore(stack);long value=counter(t,"Charge"),spent=counter(t,"Spent"),received=counter(t,"Received");if(value>dev.elementalcircuits.adapter.persistence.CarrierRepository.load(stack).rules().blockCapacity() || value>received || received-value!=spent)throw new IllegalArgumentException("固定法阵储量或收支不守恒");return value;}
    @Override public long available(){return available(carrier());}
    @Override public boolean debit(long amount){Fixed.checked(amount);long charge=available(),spent=store().getLong("Spent");if(amount==0)return true;if(amount>charge || spent<0 || spent>Long.MAX_VALUE-amount)return false;var data=store();data.putLong("Charge",charge-amount);data.putLong("Spent",spent+amount);dev.elementalcircuits.adapter.persistence.ItemData.put(carrier,"FixedCircuit",data);setChanged();return true;}
    public long charge(ServerPlayer player){
        if(!allowed(player))throw new IllegalArgumentException("仅所有者可在8格内为法阵充能");
        long amount=Math.min(assembly().rules().chargeStep(),assembly().rules().blockCapacity()-available()),received=store().getLong("Received");
        if(amount==0)throw new IllegalArgumentException("固定法阵储量已满");if(received<0 || received>Long.MAX_VALUE-amount)throw new IllegalArgumentException("充能计数已耗尽");
        if(!PlayerMana.get(player).debit(amount))throw new IllegalArgumentException("玩家魔力不足，本次需要"+Fixed.display(amount));
        var data=store();data.putLong("Charge",available()+amount);data.putLong("Received",received+amount);dev.elementalcircuits.adapter.persistence.ItemData.put(carrier,"FixedCircuit",data);PlayerMana.save(player);setChanged();return amount;
    }
    public Vec3 direction(){return Vec3.atLowerCornerOf(getBlockState().getValue(CircuitBlock.FACING).getNormal());}
    public Vec3 origin(){return Vec3.atCenterOf(worldPosition).add(direction().scale(.65)).add(0,.1,0);}
    @Override public void onLoad(){super.onLoad();if(level!=null && !level.isClientSide && invalid==null)try{refreshMaterial();}catch(RuntimeException ignored){/* Keep damaged source data for the existing fault boundary. */}WorldCircuits.register(this);}
    @Override public void setRemoved(){WorldCircuits.unregister(this);super.setRemoved();}
    @Override protected void saveAdditional(CompoundTag tag,net.minecraft.core.HolderLookup.Provider registries){super.saveAdditional(tag,registries);if(invalid!=null)tag.merge(invalid);else {dev.elementalcircuits.adapter.persistence.CarrierRepository.commit(carrier);tag.put("Carrier",carrier.save(registries));}}
    @Override public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries){return new CompoundTag();}
    @Override public void handleUpdateTag(CompoundTag tag,net.minecraft.core.HolderLookup.Provider registries){ /* Client needs only the block facing; assembly remains server-owned. */ }
    @Override protected void loadAdditional(CompoundTag tag,net.minecraft.core.HolderLookup.Provider registries){super.loadAdditional(tag,registries);invalid=null;try{
        if(!tag.contains("Carrier",Tag.TAG_COMPOUND))throw new IllegalArgumentException("固定法阵缺少完整载体");
        ItemStack loaded=dev.elementalcircuits.adapter.persistence.NativeStackCodec.read(registries,tag.getCompound("Carrier"));if(!loaded.is(ElementalCircuits.CIRCUIT_ITEM.get()) || loaded.getCount()!=1)throw new IllegalArgumentException("固定法阵载体非法");carrier=loaded;owner();available();
    }catch(RuntimeException e){invalid=tag.copy();}}
}
