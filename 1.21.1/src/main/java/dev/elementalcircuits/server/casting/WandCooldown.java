package dev.elementalcircuits.server.casting;

import dev.elementalcircuits.adapter.persistence.CooldownComponent;
import dev.elementalcircuits.adapter.resources.CooldownResources;
import dev.elementalcircuits.domain.casting.CastCooldown;
import dev.elementalcircuits.server.equipment.WandEquipment;

import net.minecraft.world.item.ItemStack;

/** One physical wand owns the release interval, including spells supplied by its offhand book. */
public final class WandCooldown {
    private WandCooldown() {}

    public static CastCooldown state(ItemStack stack) {
        return stack.getOrDefault(CooldownComponent.TYPE.get(), CastCooldown.READY);
    }

    public static void tick(ItemStack stack) {
        CastCooldown current = state(stack);
        if (!current.ready()) stack.set(CooldownComponent.TYPE.get(), current.tick());
    }

    public static boolean tryRelease(ItemStack stack) {
        return tryRelease(stack, WandEquipment.stats(stack).speed());
    }

    public static boolean tryRelease(ItemStack stack, long speed) {
        CastCooldown current = state(stack);
        if (!current.ready() || WandEquipment.broken(stack)) return false;
        stack.set(CooldownComponent.TYPE.get(), current.release(CooldownResources.rules(), speed));
        return true;
    }
}
