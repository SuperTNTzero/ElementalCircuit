package dev.elementalcircuits.server;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.WandResources;
import dev.elementalcircuits.domain.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Authoritative component installation. Wear never resets on replacement. */
public final class WandEquipment {
    private static final String KEY="WandAssembly";
    private WandEquipment(){ }
    public static WandParts.Assembly assembly(ItemStack stack){
        if(dev.elementalcircuits.adapter.persistence.ItemData.read(stack)==null || !dev.elementalcircuits.adapter.persistence.ItemData.read(stack).contains(KEY))return WandResources.definitions().basic(); // explicit legacy defaults, no free items
        CompoundTag t=dev.elementalcircuits.adapter.persistence.ItemData.read(stack).getCompound(KEY);
        if(t.getInt("Version")!=1 || !t.contains("Remainder",Tag.TAG_LONG))throw new IllegalArgumentException("未知法杖部件现场");
        WandParts.Assembly a=new WandParts.Assembly(t.getString("Head"),t.getString("Body"),t.getString("Tail"));a.stats(WandResources.definitions());
        // v1 cadence remainder is validated for compatibility; it no longer changes VM speed.
        long r=t.getLong("Remainder");if(r<0 || r>=Fixed.ONE)throw new IllegalArgumentException("非法施法余数");
        return a;
    }
    public static WandParts.Stats stats(ItemStack stack){return assembly(stack).stats(WandResources.definitions());}
    private static void write(ItemStack stack,WandParts.Assembly a,long remainder){
        CompoundTag t=new CompoundTag();t.putInt("Version",1);t.putString("Head",a.head());t.putString("Body",a.body());t.putString("Tail",a.tail());t.putLong("Remainder",remainder);
        dev.elementalcircuits.adapter.persistence.ItemData.put(stack,KEY,t);
    }
    public static boolean broken(ItemStack stack){return stack.getDamageValue()>=stats(stack).durability();}
    public static void wear(ItemStack stack){if(!broken(stack))stack.setDamageValue(stack.getDamageValue()+1);}
    public static void repair(ServerPlayer player,ItemStack wand){
        if(wand.getDamageValue()==0)throw new IllegalArgumentException("法杖无需修缮");
        for(int i=0;i<player.getInventory().getContainerSize();i++){
            ItemStack material=player.getInventory().getItem(i);
            if(material.is(ElementalCircuits.WOOD_PLATE.get())){material.shrink(1);wand.setDamageValue(Math.max(0,wand.getDamageValue()-48));return;}
        }
        throw new IllegalArgumentException("修缮需要一张木质基板");
    }
    public static void install(ServerPlayer player,ItemStack wand,String id){
        if(dev.elementalcircuits.adapter.persistence.CarrierRepository.load(wand).powered())throw new IllegalArgumentException("请先停供，再更换部件");
        WandParts.Part next=WandResources.definitions().get(id);WandParts.Assembly current=assembly(wand);
        if(current.at(next.slot()).equals(id))throw new IllegalArgumentException("该部件已经安装");
        int slot=-1;for(int i=0;i<player.getInventory().getContainerSize();i++)if(player.getInventory().getItem(i).is(ElementalCircuits.PARTS.get(id).get())){slot=i;break;}
        if(slot<0)throw new IllegalArgumentException("背包中缺少："+next.title());
        WandParts.Assembly replacement=current.replace(next);replacement.stats(WandResources.definitions());
        // Validate everything before the first inventory mutation; no tick or blueprint reset.
        player.getInventory().getItem(slot).shrink(1);
        ItemStack old=new ItemStack(ElementalCircuits.PARTS.get(current.at(next.slot())).get());
        write(wand,replacement,0);
        if(!player.getInventory().add(old))player.drop(old,false);
    }
}
