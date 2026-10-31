package dev.elementalcircuits.server;

import dev.elementalcircuits.adapter.CooldownResources;
import dev.elementalcircuits.adapter.persistence.CooldownComponent;
import dev.elementalcircuits.domain.CastCooldown;
import net.minecraft.world.item.ItemStack;

/** One physical wand owns the release interval, including spells supplied by its offhand book. */
public final class WandCooldown {
    private WandCooldown() {}
    public static CastCooldown state(ItemStack stack) { return stack.getOrDefault(CooldownComponent.TYPE.get(),CastCooldown.READY); }
    public static void tick(ItemStack stack) {
        CastCooldown current=state(stack);
        if(!current.ready()) stack.set(CooldownComponent.TYPE.get(),current.tick());
    }
    public static boolean tryRelease(ItemStack stack) {
        CastCooldown current=state(stack);
        if(!current.ready() || WandEquipment.broken(stack)) return false;
        stack.set(CooldownComponent.TYPE.get(),current.release(CooldownResources.rules(),WandEquipment.stats(stack).speed()));
        return true;
    }
}
