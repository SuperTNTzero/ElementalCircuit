package dev.elementalcircuits.server.mana;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Consumables introduce new energy, independently of outstanding source refund receipts. */
public final class InstantMana {
    public static long restore(LivingEntity target, long amount) {
        if (!(target instanceof ServerPlayer player)) return 0;
        long accepted = PlayerMana.get(player).restore(amount);
        PlayerMana.save(player);
        return accepted;
    }

    private InstantMana() {}
}
