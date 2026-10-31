package dev.elementalcircuits.server;

import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

/** Fail closed while preserving original state; no reset or mana refill. */
public final class CarrierFaults {
    private CarrierFaults(){}
    public static void report(ItemStack stack,ServerPlayer player){
        var root=stack.getOrCreateTag();
        boolean malformedRoot=root.contains("ElementalCircuit") && !root.contains("ElementalCircuit",Tag.TAG_COMPOUND);
        var flags=malformedRoot?root:stack.getOrCreateTagElement("ElementalCircuit");
        String key=malformedRoot?"ElementalCircuitFault":"Corrupt";
        if(!flags.getBoolean(key)){
            flags.putBoolean(key,true);
            if(player!=null)player.sendSystemMessage(Component.literal("法阵数据校验失败，已保留原始数据并停用；不会重置或补充魔力。"));
        }
    }
}
