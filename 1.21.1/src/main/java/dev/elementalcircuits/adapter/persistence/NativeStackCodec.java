package dev.elementalcircuits.adapter.persistence;

import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;

/** Explicitly upgrades the private carrier stack nested inside a mod block entity. */
public final class NativeStackCodec {
    private NativeStackCodec(){}
    public static ItemStack read(HolderLookup.Provider registries,CompoundTag encoded){
        CompoundTag data=encoded;
        if(encoded.contains("Count") || encoded.contains("tag")){
            if(!encoded.contains("Count",Tag.TAG_BYTE) || encoded.getByte("Count")!=1 || encoded.contains("tag") && !encoded.contains("tag",Tag.TAG_COMPOUND))throw new IllegalArgumentException("旧固定载体物品格式非法");
            var updated=DataFixers.getDataFixer().update(References.ITEM_STACK,new Dynamic<>(NbtOps.INSTANCE,encoded.copy()),3465,SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            if(!(updated.getValue() instanceof CompoundTag fixed))throw new IllegalArgumentException("旧载体升级失败");data=fixed;
        }
        return ItemStack.parseOptional(registries,data);
    }
}
