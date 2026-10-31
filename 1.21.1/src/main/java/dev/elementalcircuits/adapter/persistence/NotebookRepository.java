package dev.elementalcircuits.adapter.persistence;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.JsonCodecs;
import dev.elementalcircuits.domain.BlueprintVolume;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.*;
import java.util.UUID;

/** Notebook pages are immutable creation configurations, never live runtime instances. */
public final class NotebookRepository {
    private static final int LIMIT=524288;
    private NotebookRepository(){}
    public static BlueprintVolume load(ItemStack stack){
        if(!stack.is(ElementalCircuits.NOTEBOOK.get()))throw new IllegalArgumentException("需要蓝图簿");
        var tag=ItemData.read(stack);
        if(!tag.contains("BlueprintVolume")){var v=BlueprintVolume.empty(UUID.randomUUID());tag.put("BlueprintVolume",JsonNbt.encode(JsonCodecs.volume(v),LIMIT));ItemData.write(stack,tag);return v;}
        var raw=tag.get("BlueprintVolume");var volume=JsonCodecs.readVolume(JsonNbt.decode(raw,LIMIT));
        if(raw instanceof StringTag){tag.put("BlueprintVolume",JsonNbt.encode(JsonCodecs.volume(volume),LIMIT));ItemData.write(stack,tag);}return volume;
    }
    public static void save(ItemStack stack,BlueprintVolume volume){
        if(!load(stack).identity().equals(volume.identity()))throw new IllegalArgumentException("蓝图簿身份不匹配");
        var encoded=JsonNbt.encode(JsonCodecs.volume(volume),LIMIT);ItemData.put(stack,"BlueprintVolume",encoded);
    }
}
