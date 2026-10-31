package dev.elementalcircuits.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import java.util.UUID;

/** Attribution survives absent entities. Absence must never grant anonymous editing or PvP rights. */
public final class CastPermissions {
    private CastPermissions() {}

    public static boolean mayAffect(ServerLevel level, UUID owner, LivingEntity target) {
        if (!target.isAlive() || target.isSpectator() || target.getUUID().equals(owner)) return false;
        if (owner != null && target instanceof Player player) {
            ServerPlayer actor = level.getServer().getPlayerList().getPlayer(owner);
            return actor != null && level.getServer().isPvpAllowed() && actor.canHarmPlayer(player);
        }
        return true;
    }

    public static boolean mayChangeBlock(ServerLevel level, UUID owner, BlockPos position) {
        if (!level.isInWorldBounds(position) || !level.hasChunkAt(position) || !level.getWorldBorder().isWithinBounds(position)) return false;
        if (owner == null) return true;
        Entity actor = level.getEntity(owner);
        return actor instanceof Player player && player.getAbilities().mayBuild && level.mayInteract(player, position);
    }
}
